package de.zwegen.zpaint.command.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import de.zwegen.zpaint.contract.ZaintLayerContracts
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Quarter-turn plan shared by document-wide and active-layer rotations. */
data class QuarterTurnPlan(val angleDegrees: Float) {
    fun rotatedDocumentSize(width: Int, height: Int): DocumentSize = DocumentSize(height, width)
}

data class DocumentSize(val width: Int, val height: Int)

private data class ContentBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int
        get() = right - left + 1

    val height: Int
        get() = bottom - top + 1
}

/** Rotates the active layer without losing pixels by expanding the document around its centre. */
interface CurrentLayerCanvasRotator {
    fun rotateAndExpandCanvas(
        layerModel: ZaintLayerContracts.Model,
        angleDegrees: Float
    )
}

class AndroidCurrentLayerCanvasRotator : CurrentLayerCanvasRotator {
    override fun rotateAndExpandCanvas(
        layerModel: ZaintLayerContracts.Model,
        angleDegrees: Float
    ) {
        val currentLayer = layerModel.currentLayer ?: return
        val sourceWidth = layerModel.width
        val sourceHeight = layerModel.height
        if (sourceWidth <= 0 || sourceHeight <= 0) return

        val target = LayerRotationBounds.expandedSize(sourceWidth, sourceHeight, angleDegrees)
        val layers = layerModel.layers
        layers.forEach { layer ->
            layer.bitmap = if (layer === currentLayer) {
                rotateBitmap(layer.bitmap, target, angleDegrees)
            } else {
                expandBitmap(layer.bitmap, target)
            }
        }
        val croppedSize = removeSharedTransparentBorder(layers, target)
        layerModel.width = croppedSize.width
        layerModel.height = croppedSize.height
    }

    private fun rotateBitmap(source: Bitmap, target: DocumentSize, angleDegrees: Float): Bitmap {
        val output = Bitmap.createBitmap(target.width, target.height, source.config ?: Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(angleDegrees)
            postTranslate(target.width / 2f, target.height / 2f)
        }
        Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun expandBitmap(source: Bitmap, target: DocumentSize): Bitmap {
        val output = Bitmap.createBitmap(target.width, target.height, source.config ?: Bitmap.Config.ARGB_8888)
        val offsetX = (target.width - source.width) / 2f
        val offsetY = (target.height - source.height) / 2f
        Canvas(output).drawBitmap(source, offsetX, offsetY, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun removeSharedTransparentBorder(
        layers: List<ZaintLayerContracts.ZaintLayer>,
        canvasSize: DocumentSize
    ): DocumentSize {
        val bounds = visibleContentBounds(layers, canvasSize) ?: return canvasSize
        if (bounds.left == 0 && bounds.top == 0 &&
            bounds.width == canvasSize.width && bounds.height == canvasSize.height
        ) {
            return canvasSize
        }
        layers.forEach { layer ->
            val source = layer.bitmap
            val cropped = Bitmap.createBitmap(bounds.width, bounds.height, source.config ?: Bitmap.Config.ARGB_8888)
            Canvas(cropped).drawBitmap(source, -bounds.left.toFloat(), -bounds.top.toFloat(), null)
            layer.bitmap = cropped
        }
        return DocumentSize(bounds.width, bounds.height)
    }

    private fun visibleContentBounds(
        layers: List<ZaintLayerContracts.ZaintLayer>,
        canvasSize: DocumentSize
    ): ContentBounds? {
        var left = canvasSize.width
        var top = canvasSize.height
        var right = -1
        var bottom = -1
        val row = IntArray(canvasSize.width)

        layers.forEach { layer ->
            val bitmap = layer.bitmap
            for (y in 0 until canvasSize.height) {
                bitmap.getPixels(row, 0, canvasSize.width, 0, y, canvasSize.width, 1)
                for (x in row.indices) {
                    if ((row[x] ushr ALPHA_SHIFT) == 0) continue
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < left || bottom < top) null else ContentBounds(left, top, right, bottom)
    }

    private companion object {
        const val ALPHA_SHIFT = 24
    }
}

/** Geometry for a layer rotation whose complete result must remain on the document canvas. */
object LayerRotationBounds {
    fun expandedSize(width: Int, height: Int, angleDegrees: Float): DocumentSize {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val rotatedWidth = roundedUp(abs(width * cos(radians)) + abs(height * sin(radians)))
        val rotatedHeight = roundedUp(abs(width * sin(radians)) + abs(height * cos(radians)))
        val targetWidth = max(width, rotatedWidth)
        val targetHeight = max(height, rotatedHeight)
        return DocumentSize(targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1))
    }

    private fun roundedUp(value: Double): Int {
        val nearestInteger = value.roundToInt()
        return if (abs(value - nearestInteger) < ROUNDING_EPSILON) nearestInteger else ceil(value).toInt()
    }

    private const val ROUNDING_EPSILON = 0.0001
}

interface LayerRotator {
    fun rotateDocumentLayer(
        layer: ZaintLayerContracts.ZaintLayer,
        documentWidth: Int,
        documentHeight: Int,
        plan: QuarterTurnPlan
    )

    fun rotateCurrentLayer(layer: ZaintLayerContracts.ZaintLayer, plan: QuarterTurnPlan)
}

/** Android bitmap adapter for quarter-turn operations. */
class AndroidLayerRotator : LayerRotator {
    override fun rotateDocumentLayer(
        layer: ZaintLayerContracts.ZaintLayer,
        documentWidth: Int,
        documentHeight: Int,
        plan: QuarterTurnPlan
    ) {
        val matrix = Matrix().apply { postRotate(plan.angleDegrees) }
        layer.bitmap = Bitmap.createBitmap(
            layer.bitmap,
            0,
            0,
            documentWidth,
            documentHeight,
            matrix,
            true
        )
    }

    override fun rotateCurrentLayer(layer: ZaintLayerContracts.ZaintLayer, plan: QuarterTurnPlan) {
        val source = layer.bitmap
        val rotated = Bitmap.createBitmap(source.width, source.height, source.config)
        val matrix = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(plan.angleDegrees)
            postTranslate(source.width / 2f, source.height / 2f)
        }
        Canvas(rotated).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        layer.bitmap = rotated
    }
}
