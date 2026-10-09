@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction.Companion.onToolBarView
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ZaintToolKind
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusbarIntegrationTest {
    @get:Rule
    var launchActivityRule: ActivityTestRule<ZaintEditorActivity> = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule: ScreenshotOnFailRule = ScreenshotOnFailRule()

    @Before
    fun setUp() { onToolBarView().performSelectTool(ZaintToolKind.BRUSH) }

    @Test
    fun statusBarButtonsShouldAllBeVisible() {
        onView(withId(R.id.zpaint_btn_top_undo)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        onView(withId(R.id.zpaint_btn_top_redo)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}
