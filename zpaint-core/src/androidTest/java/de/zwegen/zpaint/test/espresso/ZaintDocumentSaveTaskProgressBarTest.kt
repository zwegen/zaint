package de.zwegen.zpaint.test.espresso

import android.os.SystemClock
import androidx.core.widget.ContentLoadingProgressBar
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.util.wrappers.TopBarViewInteraction
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveImageProgressBarTest {

    @get:Rule
    val launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    private lateinit var activity: ZaintEditorActivity

    companion object {
        private const val IMAGE_NAME = "fileName"
    }
    @Before
    fun setUp() {
        activity = launchActivityRule.activity
    }

    @Test
    fun testProgressBarShown() {
        val progressBar = activity.findViewById<ContentLoadingProgressBar>(R.id.zpaint_content_loading_progress_bar)
        progressBar.show()
        SystemClock.sleep(501)
        onView(withId(R.id.zpaint_content_loading_progress_bar))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        progressBar.hide()
    }

    @Test
    fun testProgressBarNotShown() {
        val progressBar = activity.findViewById<ContentLoadingProgressBar>(R.id.zpaint_content_loading_progress_bar)
        progressBar.show()
        onView(withId(R.id.zpaint_content_loading_progress_bar))
            .check(ViewAssertions.matches(Matchers.not(ViewMatchers.isDisplayed())))
        progressBar.hide()
    }

    @Test
    fun testProgressBarNotShownWhenSaving() {
        TopBarViewInteraction.onTopBarView()
            .performOpenMoreOptions()
        onView(withText(R.string.menu_save_image))
            .perform(ViewActions.click())
        onView(withId(R.id.zpaint_image_name_save_text))
            .perform(replaceText(IMAGE_NAME))
        onView(withText(R.string.save_button_text))
            .perform(ViewActions.click())
        onView(withId(R.id.zpaint_content_loading_progress_bar))
            .check(ViewAssertions.matches(Matchers.not(ViewMatchers.isDisplayed())))
    }
}
