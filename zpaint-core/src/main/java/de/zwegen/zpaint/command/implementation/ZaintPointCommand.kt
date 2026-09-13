package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import de.zwegen.zpaint.command.CanvasDrawingCommand

/** Draws one immutable point sample captured when the command is created. */
class ZaintPointCommand(paint: Paint, point: PointF) : CanvasDrawingCommand() {
    private val sample = PointSample(Paint(paint), PointF(point.x, point.y))

    val paint: Paint
        get() = Paint(sample.paint)

    val point: PointF
        get() = PointF(sample.point.x, sample.point.y)

    override fun drawOn(canvas: Canvas) {
        canvas.drawPoint(sample.point.x, sample.point.y, sample.paint)
    }

    private data class PointSample(val paint: Paint, val point: PointF)
}
