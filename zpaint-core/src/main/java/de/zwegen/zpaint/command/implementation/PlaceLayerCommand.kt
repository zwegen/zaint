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

class PlaceLayerCommand(
    val deltaX: Float,
    val deltaY: Float,
    val rotationDegrees: Float,
    val scaleFactor: Float = 1f
) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        if (deltaX == 0f && deltaY == 0f && rotationDegrees == 0f && scaleFactor == 1f) {
            return
        }
        val layer = layerModel.currentLayer ?: return
        val source = layer.bitmap ?: return
        val output = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val pivotX = source.width / 2f
        val pivotY = source.height / 2f
        val matrix = Matrix().apply {
            postScale(scaleFactor, scaleFactor, pivotX, pivotY)
            postRotate(rotationDegrees, pivotX, pivotY)
            postTranslate(deltaX, deltaY)
        }
        Canvas(output).drawBitmap(source, matrix, paint)
        layer.bitmap = output
    }

    override fun freeResources() = Unit
}
