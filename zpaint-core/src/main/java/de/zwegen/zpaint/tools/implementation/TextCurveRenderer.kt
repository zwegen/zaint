package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Draws a single text line along a circular arc while keeping every glyph tangent to the arc. */
object TextCurveRenderer {
    private const val STRAIGHT_EPSILON_DEGREES = 0.01f

    fun draw(
        canvas: Canvas,
        text: String,
        paint: Paint,
        centerX: Float,
        baselineY: Float,
        curvatureDegrees: Float
    ) {
        if (text.isEmpty()) return
        if (abs(curvatureDegrees) < STRAIGHT_EPSILON_DEGREES) {
            val drawingPaint = Paint(paint).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(text, centerX, baselineY, drawingPaint)
            return
        }

        val drawingPaint = Paint(paint).apply { textAlign = Paint.Align.LEFT }
        val glyphWidths = text.map { character -> drawingPaint.measureText(character.toString()) }
        val totalWidth = drawingPaint.measureText(text).coerceAtLeast(1f)
        // A single-glyph draw does not apply Paint.letterSpacing. Distribute the difference
        // between the complete run and the individual glyph widths over its gaps, so curved
        // text uses precisely the same character spacing as straight text.
        val gapAdvance = if (glyphWidths.size > 1) {
            (totalWidth - glyphWidths.sum()) / (glyphWidths.size - 1)
        } else {
            0f
        }
        val sweepRadians = Math.toRadians(abs(curvatureDegrees).toDouble()).toFloat()
        val radius = totalWidth / sweepRadians
        val direction = if (curvatureDegrees >= 0f) 1f else -1f
        // Put the visual centre of each glyph on the arc instead of its baseline. Otherwise
        // the same circular path looks tighter in one direction and looser in the other because
        // letters extend predominantly to one side of the baseline.
        val visualCenterBaselineOffset = -(drawingPaint.ascent() + drawingPaint.descent()) / 2f
        var advanced = 0f

        text.forEachIndexed { index, character ->
            val glyph = character.toString()
            val glyphWidth = glyphWidths[index]
            val theta = (advanced + glyphWidth / 2f - totalWidth / 2f) / radius
            val x = centerX + radius * sin(theta)
            val y = baselineY + direction * radius * (1f - cos(theta))
            val rotation = Math.toDegrees(atan2(direction * sin(theta), cos(theta)).toDouble()).toFloat()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation)
            canvas.drawText(glyph, -glyphWidth / 2f, visualCenterBaselineOffset, drawingPaint)
            canvas.restore()
            advanced += glyphWidth + if (index < glyphWidths.lastIndex) gapAdvance else 0f
        }
    }

    fun sagitta(textWidth: Float, curvatureDegrees: Float): Float {
        if (abs(curvatureDegrees) < STRAIGHT_EPSILON_DEGREES) return 0f
        val sweepRadians = Math.toRadians(abs(curvatureDegrees).toDouble()).toFloat()
        val radius = textWidth.coerceAtLeast(1f) / sweepRadians
        return radius * (1f - cos(sweepRadians / 2f))
    }
}
