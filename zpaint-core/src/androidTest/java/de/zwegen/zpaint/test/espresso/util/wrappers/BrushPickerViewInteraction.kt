package de.zwegen.zpaint.test.espresso.util.wrappers

import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.ViewMatchers.withId
import de.zwegen.zpaint.R

class BrushPickerViewInteraction private constructor() :
    CustomViewInteraction(Espresso.onView(withId(R.id.zpaint_layout_tool_options))) {
    fun onStrokeWidthSeekBar(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_stroke_width_seek_bar))

    fun onStrokeWidthTextView(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_stroke_width_width_text))

    fun onStrokeCapSquareView(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_stroke_ibtn_rect))

    fun onStrokeCapRoundView(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_stroke_ibtn_circle))

    companion object {
        fun onBrushPickerView(): BrushPickerViewInteraction = BrushPickerViewInteraction()
    }
}
