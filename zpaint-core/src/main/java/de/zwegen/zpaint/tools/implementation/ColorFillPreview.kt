package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
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
        IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }
    private val lock = Any()
    private val regions = mutableListOf(initialRegion)
    private var rendered: Bitmap? = null

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

    /**
     * Draws the pending fill without changing the layer. Transparent fills use the same
     * checkerboard as the drawing surface, clipped to the selected region, so the area to be
     * cleared is visible before it is confirmed.
     */
    fun draw(canvas: Canvas, checkerboardShader: Shader? = null) {
        synchronized(lock) {
            if (regions.isEmpty()) return
            val bounds = FillSelectionMask.previewBounds(regions)
            val preview = render()
            if (isTransparentSolidFill() && checkerboardShader != null) {
                val saveCount = canvas.saveLayer(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat(),
                    null
                )
                canvas.drawRect(bounds, Paint().apply { shader = checkerboardShader })
                canvas.drawBitmap(preview, bounds.left.toFloat(), bounds.top.toFloat(), maskPaint)
                canvas.restoreToCount(saveCount)
            } else {
                canvas.drawBitmap(preview, bounds.left.toFloat(), bounds.top.toFloat(), null)
            }
        }
    }

    fun bitmapForCommit(): Bitmap = synchronized(lock) {
        check(regions.isNotEmpty()) { "Color fill preview must contain a region before it is committed" }
        render().copy(Bitmap.Config.ARGB_8888, false)
    }

    /** A transparent solid fill removes the selected pixels instead of drawing an invisible stamp. */
    fun isTransparentSolidFill(): Boolean = colors.size == 1 && Color.alpha(colors.first()) == 0

    /**
     * Opaque white is irrelevant here; its alpha is the selection mask used by DST_OUT when the
     * selected area is cleared. Keeping the outer feather makes the clear operation match the
     * visible fill boundary.
     */
    fun clearMaskForCommit(): Bitmap = synchronized(lock) {
        check(regions.isNotEmpty()) { "Color fill preview must contain a region before it is committed" }
        val bounds = bounds
        val width = regions.first().canvasWidth
        val alphaMask = FillSelectionMask.alphaMask(regions, if (antialiasing) COLOR_FEATHER_PIXELS else 0)
        val pixels = IntArray(alphaMask.size) { index ->
            Color.argb(alphaMask[index], 255, 255, 255)
        }
        Bitmap.createBitmap(width, pixels.size / width, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }.let { fullMask ->
            Bitmap.createBitmap(fullMask, bounds.left, bounds.top, bounds.width(), bounds.height()).also {
                fullMask.recycle()
            }
        }
    }

    fun release() {
        synchronized(lock) {
            rendered?.recycle()
            rendered = null
        }
    }

    private fun render(): Bitmap {
        rendered?.let { return it }
        val bounds = bounds
        val width = regions.first().canvasWidth
        val height = regions.first().pixels.size / width
        val alphaMask = FillSelectionMask.alphaMask(regions, if (antialiasing) COLOR_FEATHER_PIXELS else 0)
        val fillPixels = BooleanArray(alphaMask.size) { alphaMask[it] > 0 }
        val fullPreview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (isTransparentSolidFill()) {
            // This bitmap is a selection mask for the checkerboard preview, not document data.
            fillPixels.forEachIndexed { index, isFilled ->
                if (isFilled) fullPreview.setPixel(index % width, index / width, Color.WHITE)
            }
        } else if (colors.size == 1) {
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
        applyAlphaMask(fullPreview, alphaMask, width)
        return Bitmap.createBitmap(fullPreview, bounds.left, bounds.top, bounds.width(), bounds.height())
            .also {
                fullPreview.recycle()
                rendered = it
            }
    }

    private fun invalidateRender() {
        rendered?.recycle()
        rendered = null
    }

    private fun applyAlphaMask(bitmap: Bitmap, alphaMask: IntArray, width: Int) {
        val pixels = IntArray(alphaMask.size)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)
        val outside = if (antialiasing) originalPixels?.let { FillSelectionMask.outsideColors(regions, it, COLOR_FEATHER_PIXELS) } else null
        pixels.indices.forEach { index ->
            val color = pixels[index]
            val weight = alphaMask[index]
            pixels[index] = if (weight in 1 until FillSelectionMask.ALPHA_MAX && outside != null &&
                Color.alpha(outside[index]) > 0 && !isTransparentSolidFill()
            ) {
                FillSelectionMask.blend(color, outside[index], weight)
            } else {
                Color.argb(Color.alpha(color) * weight / FillSelectionMask.ALPHA_MAX,
                    Color.red(color), Color.green(color), Color.blue(color))
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, bitmap.height)
    }

    private companion object {
        const val COLOR_FEATHER_PIXELS = 1
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    }
}
