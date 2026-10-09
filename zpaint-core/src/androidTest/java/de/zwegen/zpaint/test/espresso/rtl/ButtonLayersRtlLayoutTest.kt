@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso.rtl

import org.junit.runner.RunWith
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.rtl.util.RtlActivityTestRule
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.test.espresso.util.wrappers.BottomNavigationViewInteraction
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import de.zwegen.zpaint.R
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.action.ViewActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class ButtonLayersRtlLayoutTest {
    @JvmField
    @Rule
    var mainActivityActivityTestRule: ActivityTestRule<ZaintEditorActivity> = RtlActivityTestRule(ZaintEditorActivity::class.java, "ar")

    @JvmField
    @Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    @Test
    fun testButtonLayers() {
        BottomNavigationViewInteraction.onBottomNavigationView().onLayersClicked()
        Espresso.onView(ViewMatchers.withId(R.id.zpaint_nav_view_layer))
            .check(ViewAssertions.matches(ViewMatchers.isCompletelyDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.zpaint_layer_side_nav_button_add))
            .check(ViewAssertions.matches(ViewMatchers.isClickable()))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.zpaint_layer_side_nav_button_delete))
            .check(ViewAssertions.matches(ViewMatchers.isClickable()))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.zpaint_stroke_ibtn_circle))
            .check(ViewAssertions.matches(ViewMatchers.isClickable()))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.zpaint_stroke_ibtn_rect))
            .check(ViewAssertions.matches(ViewMatchers.isClickable()))
            .perform(ViewActions.click())
    }
}
