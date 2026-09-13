package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.contract.ZaintLayerContracts
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Replaces a painted target mask with a patch picked by the user. The selected source point is
 * the centre of that patch; it is copied in one piece and softly blended into the target.
 */
class ReplaceCommand(
    private val path: ZaintStrokePath,
    private val points: List<PointF>,
    private val brushSize: Float,
    private val sourceCenter: PointF
) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = replace(layer.bitmap)
    }

    override fun freeResources() = Unit

    private fun replace(source: Bitmap): Bitmap {
        val bounds = targetBounds(source) ?: return source
        val width = bounds.width()
        val height = bounds.height()
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        maskPaint.strokeWidth = brushSize
        val feather = BlurMaskFilter(
            edgeFeather(),
            BlurMaskFilter.Blur.NORMAL
        )
        maskPaint.maskFilter = feather
        maskPointPaint.maskFilter = feather
        Canvas(mask).apply {
            translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            drawPath(path, maskPaint)
            points.forEach { drawCircle(it.x, it.y, brushSize / 2f, maskPointPaint) }
        }

        val maskPixels = IntArray(width * height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)
        mask.recycle()
        if (maskPixels.none { it ushr 24 != 0 }) return source

        val sourceOffsetX = sourceCenter.x.roundToInt() - bounds.centerX()
        val sourceOffsetY = sourceCenter.y.roundToInt() - bounds.centerY()
        val targetPixels = IntArray(width * height)
        source.getPixels(targetPixels, 0, width, bounds.left, bounds.top, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val coverage = maskPixels[index] ushr 24 and BYTE_MASK
                if (coverage == 0) continue
                val sourceX = bounds.left + x + sourceOffsetX
                val sourceY = bounds.top + y + sourceOffsetY
                if (sourceX !in 0 until source.width || sourceY !in 0 until source.height) continue
                targetPixels[index] = blend(targetPixels[index], source.getPixel(sourceX, sourceY), coverage)
            }
        }

        return source.copy(Bitmap.Config.ARGB_8888, true).also { output ->
            output.setPixels(targetPixels, 0, width, bounds.left, bounds.top, width, height)
        }
    }

    private fun targetBounds(source: Bitmap): Bounds? {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        var hasBounds = !pathBounds.isEmpty
        points.forEach { point ->
            if (hasBounds) {
                pathBounds.union(point.x, point.y)
            } else {
                pathBounds.set(point.x, point.y, point.x, point.y)
                hasBounds = true
            }
        }
        if (!hasBounds) return null

        // A BlurMaskFilter reaches well beyond its nominal radius. Reserve the full Gaussian
        // falloff before clipping the temporary bitmap; otherwise that bitmap's rectangular
        // edge can become visible in the final blend.
        val radius = brushSize / 2f + edgeFeather() * BLUR_SIGMA_COVERAGE + ANTIALIAS_PADDING
        return Bounds(
            floor(pathBounds.left - radius).toInt().coerceAtLeast(0),
            floor(pathBounds.top - radius).toInt().coerceAtLeast(0),
            ceil(pathBounds.right + radius).toInt().coerceAtMost(source.width - 1),
            ceil(pathBounds.bottom + radius).toInt().coerceAtMost(source.height - 1)
        )
    }

    private fun blend(target: Int, replacement: Int, coverage: Int): Int {
        val inverseCoverage = BYTE_MASK - coverage
        fun component(color: Int, shift: Int): Int = color ushr shift and BYTE_MASK
        fun mix(shift: Int): Int =
            (component(target, shift) * inverseCoverage + component(replacement, shift) * coverage) / BYTE_MASK
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun edgeFeather(): Float =
        (brushSize * EDGE_FEATHER_RATIO).coerceAtLeast(MIN_EDGE_FEATHER)

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = right - left + 1
        fun height(): Int = bottom - top + 1
        fun centerX(): Int = (left + right) / 2
        fun centerY(): Int = (top + bottom) / 2
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val EDGE_FEATHER_RATIO = 0.10f
        const val MIN_EDGE_FEATHER = 3f
        const val BLUR_SIGMA_COVERAGE = 3f
        const val ANTIALIAS_PADDING = 1f
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x1
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val maskPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x1
            style = Paint.Style.FILL
        }
    }
}
