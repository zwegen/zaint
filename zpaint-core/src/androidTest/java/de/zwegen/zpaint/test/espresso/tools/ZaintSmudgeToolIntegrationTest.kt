package de.zwegen.zpaint.test.espresso.tools

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.util.UiMatcher.withProgress
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.implementation.DEFAULT_DRAG_IN_PERCENT
import de.zwegen.zpaint.tools.implementation.DEFAULT_PRESSURE_IN_PERCENT
import de.zwegen.zpaint.tools.implementation.ZaintSmudgeTool
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZaintSmudgeToolIntegrationTest {

    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    private lateinit var toolReference: ToolReference

    @Before
    fun setUp() {
        toolReference = launchActivityRule.activity.toolReference
        ToolBarViewInteraction.onToolBarView().performSelectTool(ZaintToolKind.SMUDGE)
    }

    @Test
    fun testSmudgeToolOptionsDialog() {
        val smudgeTool = toolReference.tool as ZaintSmudgeTool
        ToolBarViewInteraction.onToolBarView()
                .performSelectTool(ZaintToolKind.SMUDGE)

        val pressureInput = onView(withId(R.id.zpaint_smudge_tool_dialog_pressure_input))
        val pressureSeekBar = onView(withId(R.id.zpaint_pressure_seek_bar))
        val testPressureText = "100"
        pressureInput.check(matches(withText(DEFAULT_PRESSURE_IN_PERCENT.toString())))
        pressureInput.perform(replaceText(testPressureText), ViewActions.closeSoftKeyboard())
        pressureInput.check(matches(withText(testPressureText)))
        pressureSeekBar.check(matches(withProgress(testPressureText.toInt())))
        val expectedPressure = 1f
        Assert.assertEquals(expectedPressure, smudgeTool.maxPressure)

        val dragInput = onView(withId(R.id.zpaint_smudge_tool_dialog_drag_input))
        val dragSeekBar = onView(withId(R.id.zpaint_drag_seek_bar))
        val testDragText = "100"
        dragInput.check(matches(withText(DEFAULT_DRAG_IN_PERCENT.toString())))
        dragInput.perform(replaceText(testDragText), ViewActions.closeSoftKeyboard())
        dragInput.check(matches(withText(testDragText)))
        dragSeekBar.check(matches(withProgress(testDragText.toInt())))
        Assert.assertTrue(smudgeTool.maxSmudgeSize > 0f)
        Assert.assertEquals(smudgeTool.maxSmudgeSize, smudgeTool.minSmudgeSize)

        // Close tool options
        ToolBarViewInteraction.onToolBarView()
            .performClickSelectedToolButton()
    }
}
