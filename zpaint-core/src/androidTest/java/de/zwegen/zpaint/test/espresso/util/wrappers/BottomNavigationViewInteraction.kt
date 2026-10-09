package de.zwegen.zpaint.test.espresso.util.wrappers

import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.util.UiMatcher
import de.zwegen.zpaint.tools.ZaintToolKind
import org.hamcrest.Matchers.allOf

class BottomNavigationViewInteraction private constructor() :
    CustomViewInteraction(Espresso.onView(withId(R.id.zpaint_bottom_navigation))) {
    fun onToolsClicked(): ViewInteraction {
        return Espresso.onView(
            allOf(
                withId(com.google.android.material.R.id.navigation_bar_item_icon_view),
                ViewMatchers.isDescendantOfA(withId(R.id.action_tools))
            )
        )
            .perform(ViewActions.click())
    }

    fun onCurrentClicked(): ViewInteraction {
        return Espresso.onView(
            allOf(
                withId(com.google.android.material.R.id.navigation_bar_item_icon_view),
                ViewMatchers.isDescendantOfA(withId(R.id.action_current_tool))
            )
        )
            .perform(ViewActions.click())
    }

    fun checkShowsCurrentTool(toolType: ZaintToolKind): ViewInteraction {
        Espresso.onView(
            allOf(
                withId(com.google.android.material.R.id.navigation_bar_item_icon_view),
                ViewMatchers.isDescendantOfA(withId(R.id.action_current_tool))
            )
        )
            .check(ViewAssertions.matches(UiMatcher.withDrawable(toolType.drawableResource)))
        return Espresso.onView(withId(R.id.action_current_tool))
            .check(ViewAssertions.matches(ViewMatchers.hasDescendant(ViewMatchers.withText(toolType.nameResource))))
    }

    fun onColorClicked(): ViewInteraction {
        return Espresso.onView(
            allOf(
                withId(com.google.android.material.R.id.navigation_bar_item_icon_view),
                ViewMatchers.isDescendantOfA(withId(R.id.action_color_picker))
            )
        )
            .perform(ViewActions.click())
    }

    fun onLayersClicked(): ViewInteraction {
        return Espresso.onView(
            allOf(
                withId(com.google.android.material.R.id.navigation_bar_item_icon_view),
                ViewMatchers.isDescendantOfA(withId(R.id.action_layers))
            )
        )
            .perform(ViewActions.click())
    }

    companion object {
        @JvmStatic
        fun onBottomNavigationView() = BottomNavigationViewInteraction()
    }
}
