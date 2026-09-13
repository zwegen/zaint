package de.zwegen.zpaint.command.cut

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import de.zwegen.zpaint.command.serialization.ZaintStrokePath

enum class CutShape { RECTANGLE, OVAL, PATH }

/** Immutable description of a destructive selection operation. */
data class CutSelection(
    val center: Point,
    val width: Float,
    val height: Float,
    val rotation: Float,
    val shape: CutShape,
    val path: ZaintStrokePath?
)

interface CanvasCutRenderer {
    fun clear(canvas: Canvas, selection: CutSelection)
}

/** Android canvas adapter for clearing rectangular, oval and free-path selections. */
class AndroidCanvasCutRenderer : CanvasCutRenderer {
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        alpha = 0
    }

    override fun clear(canvas: Canvas, selection: CutSelection) {
        if (selection.shape == CutShape.PATH) {
            selection.path?.let { canvas.drawPath(it, clearPaint) }
            return
        }

        val rect = RectF(
            -selection.width / 2f,
            -selection.height / 2f,
            selection.width / 2f,
            selection.height / 2f
        )
        canvas.save()
        canvas.translate(selection.center.x.toFloat(), selection.center.y.toFloat())
        canvas.rotate(selection.rotation)
        when (selection.shape) {
            CutShape.OVAL -> canvas.drawOval(rect, clearPaint)
            CutShape.RECTANGLE -> canvas.drawRect(rect, clearPaint)
            CutShape.PATH -> Unit
        }
        canvas.restore()
    }
}
