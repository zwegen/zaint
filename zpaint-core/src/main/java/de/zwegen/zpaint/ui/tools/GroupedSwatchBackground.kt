package de.zwegen.zpaint.ui.tools

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.content.ContextCompat
import de.zwegen.zpaint.R

/** Fresh drawables per view; transparent colors alone get the checkerboard, as before. */
internal object GroupedSwatchBackground {
    fun color(context: Context, color: Int, index: Int, lastIndex: Int): Drawable {
        val border = shape(context, color, index, lastIndex)
        return if (Color.alpha(color) == 0) LayerDrawable(arrayOf(
            ContextCompat.getDrawable(context, R.drawable.zpaint_checkeredbg_repeat), border
        )) else border
    }

    fun empty(context: Context, index: Int, lastIndex: Int): Drawable =
        shape(context, Color.WHITE, index, lastIndex)

    private fun shape(context: Context, color: Int, index: Int, lastIndex: Int) = GradientDrawable().apply {
        val resources = context.resources
        // Truncate like the previous per-tool dp conversion, including fractional densities.
        val radius = resources.getDimension(R.dimen.zaint_grouped_swatch_radius).toInt().toFloat()
        shape = GradientDrawable.RECTANGLE
        cornerRadii = when (index) {
            0 -> floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
            lastIndex -> floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            else -> FloatArray(8)
        }
        setColor(color)
        setStroke(resources.getDimension(R.dimen.zaint_grouped_swatch_stroke).toInt(),
            ContextCompat.getColor(context, R.color.zaint_grouped_swatch_border))
    }
}
