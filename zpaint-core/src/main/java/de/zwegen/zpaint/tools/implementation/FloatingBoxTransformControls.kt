package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

/** Draws the shared resize and rotation controls used by floating transform boxes. */
object FloatingBoxTransformControls {
    private const val RIGHT_ANGLE = 90f
    private const val SIDES = 4
    private const val HANDLE_LENGTH_DIVISOR = 10f

    fun drawResizeMarkers(
        canvas: Canvas,
        width: Float,
        height: Float,
        strokeWidth: Float,
        shrinking: Int,
        paint: Paint
    ) {
        var currentWidth = width
        var currentHeight = height
        paint.strokeWidth = strokeWidth * 2f
        val corner = PointF(-currentWidth / 2f + shrinking, -currentHeight / 2f + shrinking)
        repeat(SIDES) {
            val horizontalLength = currentWidth / HANDLE_LENGTH_DIVISOR
            val verticalLength = currentHeight / HANDLE_LENGTH_DIVISOR
            canvas.drawLine(
                corner.x - strokeWidth / 2f,
                corner.y,
                corner.x + horizontalLength,
                corner.y,
                paint
            )
            canvas.drawLine(
                corner.x,
                corner.y - strokeWidth / 2f,
                corner.x,
                corner.y + verticalLength,
                paint
            )
            canvas.drawLine(
                corner.x + currentWidth / 2f - horizontalLength,
                corner.y,
                corner.x + currentWidth / 2f + horizontalLength,
                corner.y,
                paint
            )
            canvas.rotate(RIGHT_ANGLE)
            val oldX = corner.x
            corner.x = corner.y
            corner.y = oldX
            val oldHeight = currentHeight
            currentHeight = currentWidth
            currentWidth = oldHeight
        }
    }

    fun drawRotationArrows(
        canvas: Canvas,
        width: Float,
        height: Float,
        arcStrokeWidth: Float,
        arcRadius: Float,
        arrowHeadSize: Float,
        arrowOffset: Float,
        arcPaint: Paint,
        arrowPaint: Paint,
        arcPath: Path,
        arrowPath: Path,
        arcBounds: RectF
    ) {
        var currentWidth = width
        var currentHeight = height
        arcPaint.strokeWidth = arcStrokeWidth
        repeat(SIDES) {
            val xBase = -currentWidth / 2f - arrowOffset
            val yBase = -currentHeight / 2f - arrowOffset
            arcPath.reset()
            arcBounds.set(
                xBase - arcRadius,
                yBase - arcRadius,
                xBase + arcRadius,
                yBase + arcRadius
            )
            arcPath.addArc(arcBounds, 180f, RIGHT_ANGLE)
            canvas.drawPath(arcPath, arcPaint)
            arrowPath.run {
                reset()
                moveTo(xBase - arcRadius - arrowHeadSize, yBase)
                lineTo(xBase - arcRadius + arrowHeadSize, yBase)
                lineTo(xBase - arcRadius, yBase + arrowHeadSize)
                close()
                moveTo(xBase, yBase - arcRadius - arrowHeadSize)
                lineTo(xBase, yBase - arcRadius + arrowHeadSize)
                lineTo(xBase + arrowHeadSize, yBase - arcRadius)
                close()
            }
            canvas.drawPath(arrowPath, arrowPaint)
            val oldWidth = currentWidth
            currentWidth = currentHeight
            currentHeight = oldWidth
            canvas.rotate(RIGHT_ANGLE)
        }
    }
}
