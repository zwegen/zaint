@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso.dialog

import android.graphics.Paint
import android.graphics.Paint.Cap
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.util.EspressoUtils.DEFAULT_STROKE_WIDTH
import de.zwegen.zpaint.test.espresso.util.UiInteractions
import de.zwegen.zpaint.test.espresso.util.UiMatcher
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction.Companion.onToolBarView
import de.zwegen.zpaint.test.espresso.util.wrappers.TopBarViewInteraction.Companion.onTopBarView
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ZaintToolKind
import org.hamcrest.core.IsNot
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrushPickerIntegrationTest {
    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    @Before
    fun setUp() { onToolBarView().performSelectTool(ZaintToolKind.BRUSH) }

    private fun getCurrentToolBitmapPaint(): Paint = launchActivityRule.activity.toolPaint.paint

    private fun getCurrentToolCanvasPaint(): Paint = launchActivityRule.activity.toolPaint.previewPaint

    private fun assertStrokePaint(strokePaint: Paint, expectedStrokeWidth: Int, expectedCap: Cap) {
        val paintStrokeWidth = strokePaint.strokeWidth.toInt()
        val paintCap = strokePaint.strokeCap

        Assert.assertEquals("Stroke did not change", expectedStrokeWidth.toLong(), paintStrokeWidth.toLong())
        Assert.assertEquals("Stroke cap not $expectedCap", expectedCap, paintCap)
    }

    private fun setStrokeWidth(strokeWidth: Int, expectedStrokeWidth: Int) {
        onView(withId(R.id.zpaint_stroke_width_seek_bar))
            .perform(UiInteractions.setProgress(strokeWidth))
            .check(ViewAssertions.matches(UiMatcher.withProgress(expectedStrokeWidth)))
    }

    private fun setStrokeWidth(strokeWidth: Int) = setStrokeWidth(strokeWidth, strokeWidth)

    @Test
    fun brushPickerDialogDefaultLayoutAndToolChanges() {
        onView(withId(R.id.zpaint_brush_tool_preview))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        onView(withId(R.id.zpaint_stroke_width_seek_bar))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .check(ViewAssertions.matches(UiMatcher.withProgress(DEFAULT_STROKE_WIDTH)))
        onView(withId(R.id.zpaint_stroke_width_width_text))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .check(
                        ViewAssertions.matches(
                                withText(
                                        DEFAULT_STROKE_WIDTH.toString()
                                )
                        )
                )
        onView(withId(R.id.zpaint_stroke_ibtn_rect))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .check(ViewAssertions.matches(IsNot.not(ViewMatchers.isSelected())))
        onView(withId(R.id.zpaint_stroke_ibtn_circle))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .check(ViewAssertions.matches(ViewMatchers.isSelected()))
        setStrokeWidth(MIN_STROKE_WIDTH)
        setStrokeWidth(MIDDLE_STROKE_WIDTH)
        setStrokeWidth(MAX_STROKE_WIDTH)
        assertStrokePaint(getCurrentToolCanvasPaint(), MAX_STROKE_WIDTH, Cap.ROUND)
        onView(withId(R.id.zpaint_stroke_ibtn_rect))
                .perform(ViewActions.click())
                .check(ViewAssertions.matches(ViewMatchers.isChecked()))
        onView(withId(R.id.zpaint_stroke_ibtn_circle))
                .check(ViewAssertions.matches(ViewMatchers.isNotChecked()))
        assertStrokePaint(getCurrentToolCanvasPaint(), MAX_STROKE_WIDTH, Cap.SQUARE)
        onToolBarView().performCloseToolOptionsView()
        assertStrokePaint(getCurrentToolCanvasPaint(), MAX_STROKE_WIDTH, Cap.SQUARE)
    }

    @Test
    fun brushPickerDialogKeepStrokeOnToolChange() {
        val newStrokeWidth = 80
        setStrokeWidth(newStrokeWidth)
        onView(withId(R.id.zpaint_stroke_ibtn_rect)).perform(ViewActions.click())
        assertStrokePaint(getCurrentToolCanvasPaint(), newStrokeWidth, Cap.SQUARE)
        onToolBarView()
            .performCloseToolOptionsView()
            .performSelectTool(ZaintToolKind.LINE)
        onView(withId(R.id.zpaint_stroke_width_seek_bar))
            .check(ViewAssertions.matches(UiMatcher.withProgress(newStrokeWidth)))
        assertStrokePaint(getCurrentToolCanvasPaint(), newStrokeWidth, Cap.SQUARE)
        onToolBarView().performCloseToolOptionsView()
    }

    @Test
    fun brushPickerDialogMinimumBrushWidth() {
        setStrokeWidth(0, MIN_STROKE_WIDTH)
        setStrokeWidth(MIN_STROKE_WIDTH)
        onToolBarView().performCloseToolOptionsView()
    }

    @Test
    fun brushPickerAntiAliasingOffAtMinimumBrushSize() {
        onView(withId(R.id.zpaint_stroke_width_seek_bar)).perform(UiInteractions.touchCenterLeft())
        onToolBarView().performCloseToolOptionsView()

        val bitmapPaint = getCurrentToolBitmapPaint()
        val canvasPaint = getCurrentToolCanvasPaint()

        Assert.assertFalse("BITMAP_PAINT antialiasing should be off", bitmapPaint.isAntiAlias)
        Assert.assertFalse("CANVAS_PAINT antialiasing should be off", canvasPaint.isAntiAlias)
    }

    @Test
    fun brushPickerDialogRadioButtonsBehaviour() {
        onView(withId(R.id.zpaint_stroke_ibtn_rect))
                .check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())))
        onView(withId(R.id.zpaint_stroke_ibtn_circle))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()))
        onView(withId(R.id.zpaint_stroke_ibtn_rect))
                .perform(ViewActions.click())
                .check(ViewAssertions.matches(ViewMatchers.isChecked()))
        onView(withId(R.id.zpaint_stroke_ibtn_circle))
                .check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())))
        onToolBarView().performCloseToolOptionsView()
        assertStrokePaint(getCurrentToolCanvasPaint(), DEFAULT_STROKE_WIDTH, Cap.SQUARE)
        onToolBarView().performOpenToolOptionsView()
        onView(withId(R.id.zpaint_stroke_ibtn_circle))
                .perform(ViewActions.click())
                .check(ViewAssertions.matches(ViewMatchers.isChecked()))
        onView(withId(R.id.zpaint_stroke_ibtn_rect))
                .check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())))
        assertStrokePaint(getCurrentToolCanvasPaint(), DEFAULT_STROKE_WIDTH, Cap.ROUND)
        onToolBarView().performCloseToolOptionsView()
        assertStrokePaint(getCurrentToolCanvasPaint(), DEFAULT_STROKE_WIDTH, Cap.ROUND)
    }

    @Test
    fun brushPickerDialogEditTextBehaviour() {
        onView(withId(R.id.zpaint_stroke_width_width_text))
            .perform(ViewActions.replaceText(MIDDLE_STROKE_WIDTH.toString()))
        closeSoftKeyboard()
        onView(withId(R.id.zpaint_stroke_width_width_text))
            .check(ViewAssertions.matches(withText(MIDDLE_STROKE_WIDTH.toString())))
        onView(withId(R.id.zpaint_stroke_width_seek_bar))
            .check(ViewAssertions.matches(UiMatcher.withProgress(MIDDLE_STROKE_WIDTH)))
    }

    companion object {
        private const val MIN_STROKE_WIDTH = 1
        private const val MIDDLE_STROKE_WIDTH = 50
        private const val MAX_STROKE_WIDTH = 100
    }
}
