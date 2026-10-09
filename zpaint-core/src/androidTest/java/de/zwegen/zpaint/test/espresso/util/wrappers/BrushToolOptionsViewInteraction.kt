package de.zwegen.zpaint.test.espresso.util.wrappers

import android.graphics.Paint.Cap
import de.zwegen.zpaint.R
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.core.IsNot.not

class BrushToolOptionsViewInteraction private constructor() :
        CustomViewInteraction(onView(withId(R.id.zpaint_layout_tool_options))) {

    companion object {
        fun onBrushToolOptionsView(): BrushToolOptionsViewInteraction =
                BrushToolOptionsViewInteraction()
    }

    private fun getButtonIdFromShapeDrawType(strokeCapType: Cap): Int {
        return when (strokeCapType) {
            Cap.ROUND -> R.id.zpaint_stroke_ibtn_circle
            Cap.SQUARE -> R.id.zpaint_stroke_ibtn_rect
            else -> throw IllegalArgumentException("Unsupported Cap type: $strokeCapType")
        }
    }

    fun performSelectStrokeCapType(strokeCapType: Cap): BrushToolOptionsViewInteraction {
        onView(withId(getButtonIdFromShapeDrawType(strokeCapType)))
                .perform(click())
        return this
    }

    fun checkIfStrokeCapIsChecked(strokeCapType: Cap) {
        onView(withId(getButtonIdFromShapeDrawType(strokeCapType))).check(matches(isChecked()))
    }

    fun checkIfStrokeCapIsNotChecked(strokeCapType: Cap) {
        onView(withId(getButtonIdFromShapeDrawType(strokeCapType))).check(matches(not(isChecked())))
    }
}
