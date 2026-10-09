package de.zwegen.zpaint.test.espresso.util.wrappers

import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import de.zwegen.zpaint.R

class TopBarViewInteraction : CustomViewInteraction(Espresso.onView(withId(R.id.zpaint_layout_top_bar))) {
    fun onUndoButton(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_btn_top_undo))

    fun onRedoButton(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_btn_top_redo))

    fun onCheckmarkButton(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_btn_top_checkmark))

    fun onPlusButton(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_btn_top_plus))

    fun performUndo(): TopBarViewInteraction {
        onUndoButton()
                .perform(ViewActions.click())
        return this
    }

    fun performRedo(): TopBarViewInteraction {
        onRedoButton()
                .perform(ViewActions.click())
        return this
    }

    fun performClickCheckmark(): TopBarViewInteraction {
        onCheckmarkButton()
                .perform(ViewActions.click())
        return this
    }

    fun performClickPlus(): TopBarViewInteraction {
        onPlusButton()
                .perform(ViewActions.click())
        return this
    }

    fun performOpenMoreOptions(): TopBarViewInteraction {
        Espresso.openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().targetContext)
        return this
    }

    fun onHomeClicked(): TopBarViewInteraction {
        Espresso.pressBack()
        return this
    }

    companion object {
        fun onTopBarView(): TopBarViewInteraction = TopBarViewInteraction()
    }
}
