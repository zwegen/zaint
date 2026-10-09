package de.zwegen.zpaint.test.espresso.util.wrappers

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import de.zwegen.zpaint.R

class ClipboardToolViewInteraction(viewInteraction: ViewInteraction) :
    CustomViewInteraction(viewInteraction) {

    companion object {
        fun onClipboardToolViewInteraction(): ClipboardToolViewInteraction =
            ClipboardToolViewInteraction(onView(withId(R.id.zpaint_layout_tool_specific_options)))
    }

    fun performCopy(): ClipboardToolViewInteraction {
        onView(withId(R.id.action_copy))
            .perform(click())
        return this
    }

    fun performCut(): ClipboardToolViewInteraction {
        onView(withId(R.id.action_cut))
            .perform(click())
        return this
    }

    fun performPaste(): ClipboardToolViewInteraction {
        onView(withId(R.id.action_paste))
            .perform(click())
        return this
    }
}
