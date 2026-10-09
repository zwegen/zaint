@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso.tools

import android.graphics.Typeface
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.rtl.util.RtlActivityTestRule
import de.zwegen.zpaint.test.espresso.rtl.util.RtlUiTestUtils
import de.zwegen.zpaint.test.espresso.util.EspressoUtils.configuration
import de.zwegen.zpaint.test.espresso.util.UiMatcher
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction.Companion.onToolBarView
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ZaintToolKind
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TextToolFontListTestArabic {
    private val normalStyle = Typeface.NORMAL
    private val sansSerifFontFace = Typeface.create(Typeface.SANS_SERIF, normalStyle)
    private val serifFontFace = Typeface.create(Typeface.SERIF, normalStyle)
    private val monospaceFontFace = Typeface.create(Typeface.MONOSPACE, normalStyle)

    @get:Rule
    var launchActivityRule: ActivityTestRule<ZaintEditorActivity> = RtlActivityTestRule(ZaintEditorActivity::class.java, "ar")

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    @Suppress("LongMethod")
    @Test
    fun testTextFontFaceOfFontSpinnerArabic() {
        Assert.assertEquals(View.LAYOUT_DIRECTION_RTL.toLong(), configuration.layoutDirection.toLong())
        Assert.assertTrue(RtlUiTestUtils.checkTextDirection(Locale.getDefault().displayName))
        onToolBarView()
            .performSelectTool(ZaintToolKind.TEXT)
        onView(ViewMatchers.withId(R.id.zpaint_text_tool_dialog_list_font))
            .check(
                ViewAssertions.matches(
                    UiMatcher.atPosition(
                        0,
                        ViewMatchers.hasDescendant(
                            UiMatcher.hasTypeFace(
                                sansSerifFontFace
                            )
                        )
                    )
                )
            )
        onView(ViewMatchers.withId(R.id.zpaint_text_tool_dialog_list_font))
            .check(
                ViewAssertions.matches(
                    UiMatcher.atPosition(
                        1,
                        ViewMatchers.hasDescendant(
                            UiMatcher.hasTypeFace(
                                monospaceFontFace
                            )
                        )
                    )
                )
            )
        onView(ViewMatchers.withId(R.id.zpaint_text_tool_dialog_list_font))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(2))
            .check(
                ViewAssertions.matches(
                    UiMatcher.atPosition(
                        2,
                        ViewMatchers.hasDescendant(
                            UiMatcher.hasTypeFace(
                                serifFontFace
                            )
                        )
                    )
                )
            )
    }
}
