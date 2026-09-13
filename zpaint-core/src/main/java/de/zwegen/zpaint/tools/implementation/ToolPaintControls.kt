package de.zwegen.zpaint.tools.implementation

import android.graphics.Paint
import android.graphics.Paint.Cap
import androidx.annotation.ColorInt
import de.zwegen.zpaint.tools.ToolPaint

/** Owns the mutable paint state shared by drawing tools. */
class ToolPaintControls(private val currentPaint: () -> ToolPaint) {
    fun setColor(@ColorInt color: Int) {
        currentPaint().color = color
    }

    fun setStrokeWidth(width: Int) {
        currentPaint().strokeWidth = width.toFloat()
    }

    fun setStrokeCap(cap: Cap) {
        currentPaint().strokeCap = cap
    }

    fun drawingPaint(): Paint = Paint(currentPaint().paint)

    fun removePathEffect() {
        currentPaint().paint.pathEffect = null
    }
}
