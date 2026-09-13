package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF

/** Android rendering adapter for the temporary line shown while dragging. */
class LinePreview(
    private val start: PointF?,
    private val current: PointF?
) {
    fun isVisible(): Boolean = start != null && current != null

    fun draw(canvas: Canvas, width: Int, height: Int, paint: Paint) {
        val startPoint = start ?: return
        val currentPoint = current ?: return
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawLine(startPoint.x, startPoint.y, currentPoint.x, currentPoint.y, paint)
        canvas.restore()
    }
}
