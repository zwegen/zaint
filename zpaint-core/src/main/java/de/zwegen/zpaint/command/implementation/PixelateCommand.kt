/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PixelateCommand(
    val pixelSize: Int,
    val areaMode: Boolean,
    val centerX: Float,
    val centerY: Float,
    val radius: Float
) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = pixelate(layer.bitmap, pixelSize.coerceAtLeast(MIN_PIXEL_SIZE))
    }

    override fun freeResources() = Unit

    private fun pixelate(source: Bitmap, blockSize: Int): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val width = source.width
        val height = source.height
        val featherWidth = featherWidth(blockSize)

        for (blockTop in 0 until height step blockSize) {
            val blockBottom = min(blockTop + blockSize, height)
            for (blockLeft in 0 until width step blockSize) {
                val blockRight = min(blockLeft + blockSize, width)
                val color = averageColor(source, blockLeft, blockTop, blockRight, blockBottom)
                for (y in blockTop until blockBottom) {
                    for (x in blockLeft until blockRight) {
                        val alpha = pixelateAlpha(x, y, featherWidth)
                        when {
                            !areaMode || alpha >= FULL_ALPHA -> {
                                output.setPixel(x, y, color)
                            }
                            alpha > NO_ALPHA -> {
                                output.setPixel(x, y, blend(source.getPixel(x, y), color, alpha))
                            }
                        }
                    }
                }
            }
        }

        return output
    }

    private fun averageColor(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var alpha = 0L
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val radiusSquared = radius * radius

        for (y in top until bottom) {
            for (x in left until right) {
                if (!areaMode || isInsideCircle(x, y, radiusSquared)) {
                    val color = bitmap.getPixel(x, y)
                    alpha += (color ushr 24 and BYTE_MASK).toLong()
                    red += (color ushr 16 and BYTE_MASK).toLong()
                    green += (color ushr 8 and BYTE_MASK).toLong()
                    blue += (color and BYTE_MASK).toLong()
                    count++
                }
            }
        }

        if (count == 0L) {
            return bitmap.getPixel(max(0, left), max(0, top))
        }

        return ((alpha / count).toInt() shl 24) or
            ((red / count).toInt() shl 16) or
            ((green / count).toInt() shl 8) or
            (blue / count).toInt()
    }

    private fun blend(original: Int, pixelated: Int, alpha: Float): Int {
        val inverseAlpha = FULL_ALPHA - alpha
        val blendedAlpha = blendChannel(original ushr 24, pixelated ushr 24, inverseAlpha, alpha)
        val blendedRed = blendChannel(original ushr 16, pixelated ushr 16, inverseAlpha, alpha)
        val blendedGreen = blendChannel(original ushr 8, pixelated ushr 8, inverseAlpha, alpha)
        val blendedBlue = blendChannel(original, pixelated, inverseAlpha, alpha)

        return (blendedAlpha shl 24) or
            (blendedRed shl 16) or
            (blendedGreen shl 8) or
            blendedBlue
    }

    private fun blendChannel(original: Int, pixelated: Int, inverseAlpha: Float, alpha: Float): Int =
        ((original and BYTE_MASK) * inverseAlpha + (pixelated and BYTE_MASK) * alpha)
            .toInt()
            .coerceIn(0, BYTE_MASK)

    private fun pixelateAlpha(x: Int, y: Int, featherWidth: Float): Float {
        if (!areaMode) {
            return FULL_ALPHA
        }

        val distance = distanceToCenter(x, y)
        if (distance >= radius) {
            return NO_ALPHA
        }

        val innerRadius = radius - featherWidth
        if (distance <= innerRadius) {
            return FULL_ALPHA
        }

        val alpha = (radius - distance) / featherWidth
        return smoothStep(alpha.coerceIn(NO_ALPHA, FULL_ALPHA))
    }

    private fun smoothStep(value: Float): Float =
        value * value * (SMOOTH_STEP_FACTOR - SMOOTH_STEP_DOUBLE * value)

    private fun featherWidth(blockSize: Int): Float =
        max(radius * FEATHER_RADIUS_FACTOR, blockSize * FEATHER_PIXEL_SIZE_FACTOR)
            .coerceIn(FEATHER_MIN, FEATHER_MAX)
            .coerceAtMost(radius)

    private fun isInsideCircle(x: Int, y: Int, radiusSquared: Float): Boolean {
        val distanceX = x + HALF_PIXEL - centerX
        val distanceY = y + HALF_PIXEL - centerY
        return distanceX * distanceX + distanceY * distanceY <= radiusSquared
    }

    private fun distanceToCenter(x: Int, y: Int): Float {
        val distanceX = x + HALF_PIXEL - centerX
        val distanceY = y + HALF_PIXEL - centerY
        return sqrt(distanceX * distanceX + distanceY * distanceY)
    }

    companion object {
        private const val MIN_PIXEL_SIZE = 1
        private const val BYTE_MASK = 0xff
        private const val HALF_PIXEL = 0.5f
        private const val FULL_ALPHA = 1f
        private const val NO_ALPHA = 0f
        private const val FEATHER_RADIUS_FACTOR = 0.12f
        private const val FEATHER_PIXEL_SIZE_FACTOR = 1.2f
        private const val FEATHER_MIN = 8f
        private const val FEATHER_MAX = 50f
        private const val SMOOTH_STEP_FACTOR = 3f
        private const val SMOOTH_STEP_DOUBLE = 2f
    }
}
