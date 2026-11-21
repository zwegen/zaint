package de.zwegen.zpaint.command.bitmap

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * Replaces selected RGBA pixels, including alpha zero. Fill pixels already contain antialias
 * coverage; the separate mask preserves holes and must not multiply that coverage a second time.
 */
internal object MaskedColorReplacement {
    fun apply(target: Bitmap, fill: Bitmap, mask: Bitmap, bounds: Rect) {
        require(fill.width == bounds.width() && fill.height == bounds.height())
        require(mask.width == fill.width && mask.height == fill.height)
        val clipped = Rect(bounds)
        if (!clipped.intersect(0, 0, target.width, target.height)) return
        val width = clipped.width()
        val height = clipped.height()
        val offsetX = clipped.left - bounds.left
        val offsetY = clipped.top - bounds.top
        val destination = IntArray(width * height)
        val source = IntArray(destination.size)
        val coverage = IntArray(destination.size)
        target.getPixels(destination, 0, width, clipped.left, clipped.top, width, height)
        fill.getPixels(source, 0, width, offsetX, offsetY, width, height)
        mask.getPixels(coverage, 0, width, offsetX, offsetY, width, height)
        destination.indices.forEach { index ->
            if (Color.alpha(coverage[index]) != 0) destination[index] = source[index]
        }
        target.setPixels(destination, 0, width, clipped.left, clipped.top, width, height)
    }

}
