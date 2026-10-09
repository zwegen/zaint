package de.zwegen.zpaint.test.espresso.util.wrappers

import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.util.EspressoUtils
import de.zwegen.zpaint.test.espresso.util.wrappers.BottomNavigationViewInteraction.Companion.onBottomNavigationView
import de.zwegen.zpaint.tools.ZaintToolKind
import org.hamcrest.Matchers

class ToolBarViewInteraction :
    CustomViewInteraction(Espresso.onView(withId(R.id.zpaint_toolbar))) {
    fun onSelectedToolButton(): ViewInteraction = Espresso.onView(withId(currentToolType.toolButtonID))

    fun onToolOptionsView(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_layout_tool_specific_options))

    fun performClickSelectedToolButton(): ToolBarViewInteraction {
        onBottomNavigationView()
            .onToolsClicked()
        onSelectedToolButton()
            .perform(ViewActions.click())
        return this
    }

    fun onToolsClicked(): ToolBarViewInteraction {
        onBottomNavigationView()
            .onToolsClicked()
        return this
    }

    fun performSelectTool(toolType: ZaintToolKind): ToolBarViewInteraction {
        if (currentToolType !== toolType) {
            onBottomNavigationView()
                .onToolsClicked()
            Espresso.onView(withId(toolType.toolButtonID))
                .perform(ViewActions.click())
        }
        return this
    }

    private val currentToolType: ZaintToolKind
        get() = EspressoUtils.mainActivity.toolReference.tool!!.toolType

    fun performOpenToolOptionsView(): ToolBarViewInteraction {
        if (currentToolType != ZaintToolKind.TEXT) {
            onToolOptionsView()
                .check(ViewAssertions.matches(Matchers.not(ViewMatchers.isDisplayed())))
        }
        onBottomNavigationView()
            .onCurrentClicked()
        return this
    }

    fun performCloseToolOptionsView(): ToolBarViewInteraction {
        if (currentToolType != ZaintToolKind.TEXT) {
            onToolOptionsView()
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
        onBottomNavigationView()
            .onCurrentClicked()
        return this
    }

    companion object {
        fun onToolBarView(): ToolBarViewInteraction = ToolBarViewInteraction()
    }
}
