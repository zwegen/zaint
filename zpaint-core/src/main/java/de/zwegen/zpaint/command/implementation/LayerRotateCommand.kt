/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.bitmap.AndroidCurrentLayerCanvasRotator
import de.zwegen.zpaint.command.bitmap.LayerRotationBounds
import de.zwegen.zpaint.contract.ZaintLayerContracts

class LayerRotateCommand(angle: Float) : Command {

    var angle = angle; private set

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        AndroidCurrentLayerCanvasRotator().rotateAndExpandCanvas(layerModel, angle)
    }

    override fun freeResources() {
        // No resources to free
    }

    companion object {
        fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
            val target = LayerRotationBounds.expandedSize(bitmap.width, bitmap.height, angle)
            val rotatedBitmap = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
            val rotateMatrix = Matrix().apply {
                postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                postRotate(angle)
                postTranslate(target.width / 2f, target.height / 2f)
            }
            Canvas(rotatedBitmap).drawBitmap(bitmap, rotateMatrix, Paint(Paint.FILTER_BITMAP_FLAG))
            return rotatedBitmap
        }
    }
}
