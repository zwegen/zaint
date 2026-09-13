package de.zwegen.zpaint.iotasks

import kotlin.math.floor
import kotlin.math.min

data class BitmapSize(val width: Int, val height: Int)

object DisplayBitmapScaler {
    fun calculateTargetSizeForVisibleDrawingSurface(
        sourceWidth: Int,
        sourceHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): BitmapSize = calculateTargetSize(sourceWidth, sourceHeight, maxWidth, maxHeight)

    fun calculateTargetSize(
        sourceWidth: Int,
        sourceHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): BitmapSize {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(maxWidth > 0 && maxHeight > 0)

        if (sourceWidth <= maxWidth && sourceHeight <= maxHeight) {
            return BitmapSize(sourceWidth, sourceHeight)
        }

        val scale = min(maxWidth.toFloat() / sourceWidth, maxHeight.toFloat() / sourceHeight)
        return BitmapSize(
            floor(sourceWidth * scale).toInt().coerceAtLeast(1),
            floor(sourceHeight * scale).toInt().coerceAtLeast(1)
        )
    }

    /**
     * Chooses a power-of-two decoder sample without undershooting the target
     * on either axis, preserving enough bitmap detail for Perspective to fit
     * the loaded image to the visible drawing surface.
     */
    fun calculateInSampleSize(sourceWidth: Int, sourceHeight: Int, targetSize: BitmapSize): Int {
        require(sourceWidth > 0 && sourceHeight > 0)

        var sampleSize = 1
        while (sourceWidth / (sampleSize * 2) >= targetSize.width &&
            sourceHeight / (sampleSize * 2) >= targetSize.height
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
