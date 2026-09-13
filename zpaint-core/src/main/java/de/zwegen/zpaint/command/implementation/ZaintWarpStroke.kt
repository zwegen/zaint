package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/**
 * Stores the rendered result of one local deformation stroke.
 *
 * The source layer is sampled while the command is created. The command then owns only the
 * affected bitmap region, keeping undo replay deterministic and memory bounded.
 */
class ZaintWarpStroke(
    source: Bitmap,
    points: List<PointF>,
    radius: Float,
    strength: Int
) : Command {
    private val renderedStroke = render(source, points, radius, strength)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        canvas.drawBitmap(renderedStroke.bitmap, renderedStroke.left.toFloat(), renderedStroke.top.toFloat(), null)
    }

    override fun freeResources() {
        if (!renderedStroke.bitmap.isRecycled) renderedStroke.bitmap.recycle()
    }

    private data class RenderedStroke(val bitmap: Bitmap, val left: Int, val top: Int)

    private data class StrokeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private companion object {
        private const val MIN_RADIUS = 1f
        private const val MAX_STRENGTH = 100

        fun render(source: Bitmap, points: List<PointF>, radius: Float, strength: Int): RenderedStroke {
            val boundedRadius = radius.coerceAtLeast(MIN_RADIUS)
            val boundedStrength = strength.coerceIn(0, MAX_STRENGTH) / MAX_STRENGTH.toFloat()
            val warpPoints = points.map { WarpPoint(it.x, it.y) }
            val bounds = calculateBounds(source, points, boundedRadius)
            val output = Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
            if (points.size < 2 || boundedStrength == 0f) {
                Canvas(output).drawBitmap(source, -bounds.left.toFloat(), -bounds.top.toFloat(), null)
                return RenderedStroke(output, bounds.left, bounds.top)
            }

            for (localY in 0 until bounds.height) {
                val canvasY = bounds.top + localY.toFloat()
                for (localX in 0 until bounds.width) {
                    val canvasX = bounds.left + localX.toFloat()
                    val sample = WarpStrokeMath.inverseWarp(canvasX, canvasY, warpPoints, boundedRadius, boundedStrength)
                    output.setPixel(localX, localY, sampleBilinear(source, sample.x, sample.y))
                }
            }
            return RenderedStroke(output, bounds.left, bounds.top)
        }

        private fun calculateBounds(source: Bitmap, points: List<PointF>, radius: Float): StrokeBounds {
            if (points.isEmpty()) return StrokeBounds(0, 0, 1, 1)
            var left = points.minOf { it.x }
            var top = points.minOf { it.y }
            var right = points.maxOf { it.x }
            var bottom = points.maxOf { it.y }
            val extension = radius + maxSegmentLength(points)
            left -= extension
            top -= extension
            right += extension
            bottom += extension
            return StrokeBounds(
                floor(left).toInt().coerceIn(0, source.width - 1),
                floor(top).toInt().coerceIn(0, source.height - 1),
                ceil(right).toInt().coerceIn(1, source.width),
                ceil(bottom).toInt().coerceIn(1, source.height)
            )
        }

        private fun maxSegmentLength(points: List<PointF>): Float =
            points.zipWithNext().maxOfOrNull { (start, end) ->
                hypot(end.x - start.x, end.y - start.y)
            } ?: 0f

        private fun sampleBilinear(bitmap: Bitmap, x: Float, y: Float): Int {
            val left = floor(x).toInt().coerceIn(0, bitmap.width - 1)
            val top = floor(y).toInt().coerceIn(0, bitmap.height - 1)
            val right = min(left + 1, bitmap.width - 1)
            val bottom = min(top + 1, bitmap.height - 1)
            val horizontal = (x - floor(x)).coerceIn(0f, 1f)
            val vertical = (y - floor(y)).coerceIn(0f, 1f)
            return blend(blend(bitmap.getPixel(left, top), bitmap.getPixel(right, top), horizontal),
                blend(bitmap.getPixel(left, bottom), bitmap.getPixel(right, bottom), horizontal), vertical)
        }

        private fun blend(first: Int, second: Int, amount: Float): Int {
            fun channel(shift: Int): Int =
                (((first ushr shift and 0xff) * (1f - amount)) + ((second ushr shift and 0xff) * amount)).toInt()
            return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
