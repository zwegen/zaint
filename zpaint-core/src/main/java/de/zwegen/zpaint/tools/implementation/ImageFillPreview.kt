package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import de.zwegen.zpaint.tools.helper.FillColorMatcher
import java.util.ArrayDeque

/** The connected target area and the transient image rendered inside it. */
internal data class ImageFillRegion(
    val canvasWidth: Int,
    val pixels: BooleanArray,
    val bounds: Rect
)

internal object ImageFillRegionFinder {
    fun find(bitmap: Bitmap, x: Int, y: Int, tolerance: Float): ImageFillRegion? {
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return null

        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        val matching = BooleanArray(source.size)
        val visited = BooleanArray(source.size)
        val queue = ArrayDeque<Int>()
        val matcher = FillColorMatcher(source[y * width + x], tolerance)
        var left = width
        var top = height
        var right = -1
        var bottom = -1

        queue.add(y * width + x)
        visited[y * width + x] = true
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            if (!matcher.matches(source[index])) continue

            matching[index] = true
            val column = index % width
            val row = index / width
            left = minOf(left, column)
            top = minOf(top, row)
            right = maxOf(right, column)
            bottom = maxOf(bottom, row)

            enqueue(index - 1, column > 0, visited, queue)
            enqueue(index + 1, column + 1 < width, visited, queue)
            enqueue(index - width, row > 0, visited, queue)
            enqueue(index + width, row + 1 < height, visited, queue)
        }

        if (right < left || bottom < top) return null
        return ImageFillRegion(width, matching, Rect(left, top, right + 1, bottom + 1))
    }

    private fun enqueue(index: Int, isValid: Boolean, visited: BooleanArray, queue: ArrayDeque<Int>) {
        if (isValid && !visited[index]) {
            visited[index] = true
            queue.add(index)
        }
    }
}

/**
 * Renders the source bitmap only in [region]. Nothing reaches the document until its bitmap is
 * handed to the command timeline by the fill tool.
 */
internal class ImageFillPreview(
    private val source: Bitmap,
    initialRegion: ImageFillRegion,
    initialCenter: PointF
) {
    private val lock = Any()
    private val regions = mutableListOf(initialRegion)
    val bounds: Rect
        get() = synchronized(lock) { combinedBounds() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val center = PointF(initialCenter.x, initialCenter.y)
    private var scale = 1f
    private var rotation = 0f
    private var rendered: Bitmap? = null

    fun moveBy(dx: Float, dy: Float) {
        synchronized(lock) {
            center.offset(dx, dy)
            invalidateRender()
        }
    }

    fun transformBy(scaleFactor: Float, rotationDelta: Float, dx: Float, dy: Float) {
        synchronized(lock) {
            scale = (scale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
            rotation += rotationDelta
            center.offset(dx, dy)
            invalidateRender()
        }
    }

    fun contains(coordinate: PointF): Boolean = synchronized(lock) {
        val x = coordinate.x.toInt()
        val y = coordinate.y.toInt()
        regions.any { region ->
            x in region.bounds.left until region.bounds.right &&
                y in region.bounds.top until region.bounds.bottom &&
                region.pixels[y * region.canvasWidth + x]
        }
    }

    fun addRegion(region: ImageFillRegion) {
        synchronized(lock) {
            require(region.canvasWidth == regions.first().canvasWidth) {
                "Image fill regions must share a canvas"
            }
            regions += region
            invalidateRender()
        }
    }

    fun removeRegionAt(coordinate: PointF): Boolean {
        return synchronized(lock) {
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
    }

    val isEmpty: Boolean
        get() = synchronized(lock) { regions.isEmpty() }

    fun draw(canvas: Canvas) {
        synchronized(lock) {
            if (regions.isEmpty()) return
            val bitmap = render()
            val bounds = combinedBounds()
            canvas.drawBitmap(bitmap, bounds.left.toFloat(), bounds.top.toFloat(), null)
        }
    }

    fun bitmapForCommit(): Bitmap {
        return synchronized(lock) {
            check(regions.isNotEmpty()) { "Image fill preview must contain a region before it is committed" }
            render().copy(Bitmap.Config.ARGB_8888, false)
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
        val width = bounds.width()
        val height = bounds.height()
        val preview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(preview).apply {
            translate(center.x - bounds.left, center.y - bounds.top)
            rotate(rotation)
            scale(scale, scale)
            drawBitmap(source, -source.width / 2f, -source.height / 2f, paint)
        }
        applyRegionMask(preview)
        rendered = preview
        return preview
    }

    private fun invalidateRender() {
        rendered?.recycle()
        rendered = null
    }

    private fun applyRegionMask(bitmap: Bitmap) {
        val width = bitmap.width
        val pixels = IntArray(width * bitmap.height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)
        val bounds = bounds
        for (localY in 0 until bitmap.height) {
            val sourceY = bounds.top + localY
            for (localX in 0 until width) {
                val sourceX = bounds.left + localX
                if (regions.none { region ->
                        region.pixels[sourceY * region.canvasWidth + sourceX]
                    }) {
                    pixels[localY * width + localX] = 0
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, bitmap.height)
    }

    private fun combinedBounds(): Rect {
        val first = regions.firstOrNull() ?: return Rect()
        return regions.drop(1).fold(Rect(first.bounds)) { combined, next ->
            combined.apply { union(next.bounds) }
        }
    }

    private companion object {
        const val MIN_SCALE = 0.05f
        const val MAX_SCALE = 20f
    }
}
