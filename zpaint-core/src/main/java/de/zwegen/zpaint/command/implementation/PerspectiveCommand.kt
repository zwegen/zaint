/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts

class PerspectiveCommand(
    val sourcePoints: FloatArray,
    val maximumBitmapResolution: Int
) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        if (sourcePoints.size != POINT_COORDINATE_COUNT) {
            return
        }
        val currentLayer = layerModel.currentLayer ?: return
        val source = currentLayer.bitmap ?: return

        transformBitmap(source, sourcePoints)?.let { currentLayer.bitmap = it }
    }

    override fun freeResources() = Unit

    companion object {
        private const val POINT_COUNT = 4
        private const val COORDINATES_PER_POINT = 2
        private const val POINT_COORDINATE_COUNT = POINT_COUNT * COORDINATES_PER_POINT

        /**
         * Renders the same perspective mapping used by the command without changing a layer.
         * PerspectiveTool uses this for its temporary drag preview.
         */
        fun transformBitmap(source: Bitmap, sourcePoints: FloatArray): Bitmap? {
            if (sourcePoints.size != POINT_COORDINATE_COUNT) {
                return null
            }
            val layerCorners = floatArrayOf(
                0f, 0f,
                (source.width - 1).toFloat(), 0f,
                (source.width - 1).toFloat(), (source.height - 1).toFloat(),
                0f, (source.height - 1).toFloat()
            )
            val matrix = Matrix()
            if (!matrix.setPolyToPoly(layerCorners, 0, sourcePoints, 0, POINT_COUNT)) {
                return null
            }

            return Bitmap.createBitmap(
                source.width,
                source.height,
                source.config ?: Bitmap.Config.ARGB_8888
            ).also { output ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                Canvas(output).drawBitmap(source, matrix, paint)
            }
        }
    }
}
