package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import de.zwegen.zpaint.command.bitmap.MaskedColorReplacement
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import de.zwegen.zpaint.command.implementation.GradientFillCommand

/** Transient, undoable-on-confirmation preview for solid and gradient color fills with soft edges. */
internal class ColorFillPreview(
    initialRegion: ImageFillRegion,
    private val colors: IntArray,
    private val direction: FillGradientDirection,
    original: Bitmap? = null,
    private var antialiasing: Boolean = true
) {
    /**
     * The preview is changed on the UI thread but drawn by the drawing-surface thread.
     * Keep bitmap creation, drawing and recycling in one critical section so a Canvas never
     * receives a bitmap that has just been released by a tool change or preview reset.
     */
    private val originalPixels = original?.let { bitmap ->
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }
    private val lock = Any()
    private val regions = mutableListOf(initialRegion)
    private var rendered: Bitmap? = null
    private var selectionMask: Bitmap? = null
    private var renderedLayer: Bitmap? = null

    val bounds: Rect
        get() = synchronized(lock) { FillSelectionMask.previewBounds(regions) }

    val isEmpty: Boolean
        get() = synchronized(lock) { regions.isEmpty() }

    fun contains(coordinate: PointF): Boolean = synchronized(lock) {
        val x = coordinate.x.toInt()
        val y = coordinate.y.toInt()
        regions.any { region ->
            x in region.bounds.left until region.bounds.right &&
                y in region.bounds.top until region.bounds.bottom &&
                region.pixels[y * region.canvasWidth + x]
        }
    }

    fun setAntialiasing(enabled: Boolean) {
        synchronized(lock) {
            if (antialiasing == enabled) return
            antialiasing = enabled
            invalidateRender()
        }
    }

    fun addRegion(region: ImageFillRegion) {
        synchronized(lock) {
            require(region.canvasWidth == regions.first().canvasWidth) {
                "Color fill regions must share a canvas"
            }
            regions += region
            invalidateRender()
        }
    }

    fun removeRegionAt(coordinate: PointF): Boolean = synchronized(lock) {
        val x = coordinate.x.toInt()
        val y = coordinate.y.toInt()
        val index = regions.indexOfFirst { region ->
            x in region.bounds.left until region.bounds.right &&
                y in region.bounds.top until region.bounds.bottom &&
                region.pixels[y * region.canvasWidth + x]
        }
        if (index < 0) return@synchronized false
        regions.removeAt(index)
        invalidateRender()
        true
    }

    /** Draws the same replacement as commit, then applies the active layer opacity once. */
    fun drawLayerPreview(canvas: Canvas, layer: Bitmap, layerPaint: Paint): Boolean = synchronized(lock) {
        if (regions.isEmpty()) return@synchronized false
        val preview = renderedLayer ?: layer.copy(Bitmap.Config.ARGB_8888, true).also {
            MaskedColorReplacement.apply(it, render(), mask(), bounds)
            renderedLayer = it
        }
        canvas.drawBitmap(preview, 0f, 0f, layerPaint)
        true
    }

    fun bitmapForCommit(): Bitmap = synchronized(lock) {
        check(regions.isNotEmpty()) { "Color fill preview must contain a region before it is committed" }
        render().copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Selection coverage is independent of the selected color's alpha, including alpha zero. */
    fun selectionMaskForCommit(): Bitmap = synchronized(lock) {
        check(regions.isNotEmpty()) { "Color fill preview must contain a region before it is committed" }
        mask().copy(Bitmap.Config.ARGB_8888, false)
    }

    fun release() = synchronized(lock) { invalidateRender() }

    private fun mask(): Bitmap {
        selectionMask?.let { return it }
        val bounds = bounds
        val width = regions.first().canvasWidth
        val alphaMask = FillSelectionMask.alphaMask(regions, if (antialiasing) COLOR_FEATHER_PIXELS else 0)
        val pixels = IntArray(bounds.width() * bounds.height()) { index ->
            val x = bounds.left + index % bounds.width()
            val y = bounds.top + index / bounds.width()
            Color.argb(alphaMask[y * width + x], 255, 255, 255)
        }
        return Bitmap.createBitmap(pixels, bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            .also { selectionMask = it }
    }

    private fun render(): Bitmap {
        rendered?.let { return it }
        val bounds = bounds
        val width = regions.first().canvasWidth
        val height = regions.first().pixels.size / width
        val alphaMask = FillSelectionMask.alphaMask(regions, if (antialiasing) COLOR_FEATHER_PIXELS else 0)
        val fillPixels = BooleanArray(alphaMask.size) { alphaMask[it] > 0 }
        val fullPreview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (colors.size == 1) {
            val pixels = IntArray(alphaMask.size)
            fillPixels.forEachIndexed { index, isFilled ->
                if (isFilled) pixels[index] = colors.first()
            }
            fullPreview.setPixels(pixels, 0, width, 0, 0, width, height)
        } else {
            GradientFillCommand(
                Point(bounds.left, bounds.top),
                Paint(),
                0f,
                colors,
                direction
            ).renderPreviewInto(fullPreview, fillPixels)
        }
        applyCoverage(fullPreview, alphaMask, width)
        return Bitmap.createBitmap(fullPreview, bounds.left, bounds.top, bounds.width(), bounds.height())
            .also {
                fullPreview.recycle()
                rendered = it
            }
    }

    /**
     * Bake coverage once, blending with the unchanged side of the edge as before. The independent
     * selection mask later gates replacement; multiplying by coverage again would soften twice,
     * and blending with the old selected pixel would accumulate alpha on repeated fills.
     */
    private fun applyCoverage(bitmap: Bitmap, coverage: IntArray, width: Int) {
        val pixels = IntArray(coverage.size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)
        val outside = if (antialiasing) originalPixels?.let {
            FillSelectionMask.outsideColors(regions, it, COLOR_FEATHER_PIXELS)
        } else null
        pixels.indices.forEach { index ->
            val color = pixels[index]
            val weight = coverage[index]
            pixels[index] = if (weight in 1 until FillSelectionMask.ALPHA_MAX && outside != null) {
                FillSelectionMask.blend(color, outside[index], weight)
            } else {
                Color.argb(Color.alpha(color) * weight / FillSelectionMask.ALPHA_MAX,
                    Color.red(color), Color.green(color), Color.blue(color))
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, bitmap.height)
    }

    private fun invalidateRender() {
        rendered?.recycle()
        rendered = null
        selectionMask?.recycle()
        selectionMask = null
        renderedLayer?.recycle()
        renderedLayer = null
    }

    private companion object {
        const val COLOR_FEATHER_PIXELS = 1
    }
}
