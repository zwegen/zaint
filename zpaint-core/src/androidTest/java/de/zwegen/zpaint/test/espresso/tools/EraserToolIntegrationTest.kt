@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso.tools

import android.graphics.Color
import android.graphics.Paint.Cap
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.util.BitmapLocationProvider
import de.zwegen.zpaint.test.espresso.util.DrawingSurfaceLocationProvider
import de.zwegen.zpaint.test.espresso.util.EspressoUtils.DEFAULT_STROKE_WIDTH
import de.zwegen.zpaint.test.espresso.util.UiInteractions.setProgress
import de.zwegen.zpaint.test.espresso.util.UiInteractions.swipeAccurate
import de.zwegen.zpaint.test.espresso.util.UiInteractions.touchAt
import de.zwegen.zpaint.test.espresso.util.UiInteractions.waitFor
import de.zwegen.zpaint.test.espresso.util.UiMatcher.withProgress
import de.zwegen.zpaint.test.espresso.util.wrappers.BrushPickerViewInteraction.Companion.onBrushPickerView
import de.zwegen.zpaint.test.espresso.util.wrappers.DrawingSurfaceInteraction.Companion.onDrawingSurfaceView
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction.Companion.onToolBarView
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolPropertiesInteraction.Companion.onToolProperties
import de.zwegen.zpaint.test.espresso.util.wrappers.TopBarViewInteraction.Companion.onTopBarView
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ZaintToolKind
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EraserToolIntegrationTest {
    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    @Test
    fun testEraseOnEmptyBitmap() {
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
    }

    @Test
    fun testEraseSinglePixel() {
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
    }

    @Test
    fun testSwitchingBetweenBrushAndEraser() {
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.BRUSH)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
    }

    @Test
    fun testChangeEraserBrushSize() {
        var newStrokeWidth = 90
        onBrushPickerView().onStrokeWidthSeekBar()
            .perform(setProgress(newStrokeWidth))
            .check(matches(withProgress(newStrokeWidth)))
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onBrushPickerView().onStrokeWidthSeekBar()
            .check(
                matches(
                    allOf(
                        isDisplayed(),
                        withProgress(newStrokeWidth)
                    )
                )
            )
        onBrushPickerView().onStrokeWidthTextView()
            .check(
                matches(
                    allOf(
                        isDisplayed(),
                        withText(
                            newStrokeWidth.toString()
                        )
                    )
                )
            )
        newStrokeWidth = 80
        onBrushPickerView().onStrokeWidthSeekBar()
            .perform(setProgress(newStrokeWidth))
            .check(matches(withProgress(newStrokeWidth)))
        onToolBarView()
            .performCloseToolOptionsView()
        onToolProperties()
            .checkStrokeWidth(80f)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
    }

    @Test
    fun testChangeEraserBrushForm() {
        onBrushPickerView()
            .onStrokeWidthSeekBar()
            .perform(setProgress(70))
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onBrushPickerView()
            .onStrokeWidthSeekBar()
            .perform(setProgress(50))
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onBrushPickerView().onStrokeCapSquareView()
            .perform(click())
        onToolBarView()
            .performCloseToolOptionsView()
        onToolProperties()
            .checkCap(Cap.SQUARE)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
    }

    @Suppress("LongMethod")
    @Test
    fun testRestorePreviousToolSettings() {
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onBrushPickerView().onStrokeWidthTextView()
            .check(
                matches(
                    allOf(
                        isDisplayed(),
                        withText(
                            TEXT_DEFAULT_STROKE_WIDTH
                        )
                    )
                )
            )
        onBrushPickerView().onStrokeWidthSeekBar()
            .check(
                matches(
                    allOf(
                        isDisplayed(),
                        withProgress(DEFAULT_STROKE_WIDTH)
                    )
                )
            )
        val newStrokeWidth = 80
        onBrushPickerView().onStrokeWidthSeekBar()
            .perform(setProgress(newStrokeWidth))
            .check(matches(withProgress(newStrokeWidth)))
        onBrushPickerView().onStrokeCapSquareView()
            .perform(click())
        onToolProperties()
            .checkStrokeWidth(newStrokeWidth.toFloat())
            .checkCap(Cap.SQUARE)
        onToolBarView()
            .performCloseToolOptionsView()
            .performOpenToolOptionsView()
        onBrushPickerView().onStrokeWidthSeekBar()
            .check(matches(withProgress(newStrokeWidth)))
        val eraserStrokeWidth = 60
        onBrushPickerView().onStrokeWidthSeekBar()
            .perform(setProgress(eraserStrokeWidth))
            .check(matches(withProgress(eraserStrokeWidth)))
        onBrushPickerView().onStrokeCapRoundView()
            .perform(click())
        onToolProperties()
            .checkStrokeWidth(eraserStrokeWidth.toFloat())
            .checkCap(Cap.ROUND)
        onToolBarView()
            .performSelectTool(ZaintToolKind.BRUSH)
        onBrushPickerView().onStrokeWidthSeekBar()
            .check(matches(withProgress(eraserStrokeWidth)))
        onBrushPickerView().onStrokeCapRoundView()
            .check(matches(ViewMatchers.isSelected()))
        onToolProperties()
            .checkCap(Cap.ROUND)
            .checkStrokeWidth(eraserStrokeWidth.toFloat())
    }

    @Test
    fun fillBitmapEraseASpotThenFillTheErasedSpotCreatesNoCheckeredPattern() {
        onToolBarView()
            .performSelectTool(ZaintToolKind.FILL)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onDrawingSurfaceView().perform(
            swipeAccurate(
                DrawingSurfaceLocationProvider.HALFWAY_TOP_MIDDLE,
                DrawingSurfaceLocationProvider.HALFWAY_BOTTOM_MIDDLE
            )
        )
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.HALFWAY_TOP_MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.HALFWAY_BOTTOM_MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.FILL)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_TOP_MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_BOTTOM_MIDDLE)
    }

    @Test
    fun fillBitmapEraseASpotThenFillTheErasedSpotUndoRedoNoCheckeredPattern() {
        onToolBarView()
            .performSelectTool(ZaintToolKind.FILL)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.ERASER)
        onDrawingSurfaceView().perform(
            swipeAccurate(
                DrawingSurfaceLocationProvider.HALFWAY_TOP_MIDDLE,
                DrawingSurfaceLocationProvider.HALFWAY_BOTTOM_MIDDLE
            )
        )
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.HALFWAY_TOP_MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.HALFWAY_BOTTOM_MIDDLE)
        onToolBarView()
            .performSelectTool(ZaintToolKind.FILL)
        onDrawingSurfaceView()
            .perform(touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_TOP_MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_BOTTOM_MIDDLE)
        onTopBarView().performUndo()
        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(2000))
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.HALFWAY_TOP_MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.HALFWAY_BOTTOM_MIDDLE)
        onTopBarView().performRedo()
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_TOP_MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_BOTTOM_MIDDLE)
    }

    companion object {
        private const val TEXT_DEFAULT_STROKE_WIDTH = DEFAULT_STROKE_WIDTH.toString()
    }
}
