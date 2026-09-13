package de.zwegen.zpaint.command.clipboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.RectF

/** Immutable placement of a clipboard bitmap on a layer. */
class ClipboardPlacement(
    position: Point,
    private val width: Float,
    private val height: Float,
    private val rotation: Float
) {
    private val centerX = position.x.toFloat()
    private val centerY = position.y.toFloat()

    fun drawOn(canvas: Canvas, bitmap: Bitmap) {
        val destination = RectF(-width / 2f, -height / 2f, width / 2f, height / 2f)
        with(canvas) {
            save()
            translate(centerX, centerY)
            rotate(rotation)
            drawBitmap(bitmap, null, destination, null)
            restore()
        }
    }
}
