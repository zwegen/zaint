package de.zwegen.zpaint.test.espresso.dialog

import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.graphics.PointF
import android.os.Build
import androidx.fragment.app.DialogFragment
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.dialog.IndeterminateProgressDialog
import de.zwegen.zpaint.test.espresso.util.UiInteractions
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndeterminateProgressDialogIntegrationTest {
    @get:Rule
    var activityTestRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()
    private lateinit var dialog: DialogFragment

    @Before
    fun setUp() {
        dialog = IndeterminateProgressDialog()
        dialog.show(activityTestRule.activity.supportFragmentManager, "PROGRESS_TAG_TEST")
    }

    @After
    fun tearDown() {
        dialog.dismiss()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    @Test
    fun testDialogIsShown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Espresso.onView(withId(R.id.zpaint_progress_bar))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    @Test
    fun testDialogIsNotCancelableOnBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Espresso.pressBack()
            Espresso.onView(withId(R.id.zpaint_progress_bar))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    @Test
    fun testDialogIsNotCancelable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val metrics = Resources.getSystem().displayMetrics
            val point = PointF(-metrics.widthPixels / 4f, -metrics.heightPixels / 4f)
            Espresso.onView(withId(R.id.zpaint_progress_bar))
                .perform(UiInteractions.touchAt(point))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    @Test
    fun testDialogIsRotateAble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activityTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activityTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
