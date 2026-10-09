@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.util.wrappers.BottomNavigationViewInteraction.Companion.onBottomNavigationView
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction.Companion.onToolBarView
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ZaintToolKind
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolSelectionIntegrationTest {
    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    @Before
    fun setUp() { onToolBarView().performSelectTool(ZaintToolKind.BRUSH) }

    @Test
    fun testToolSelectionToast() {
        val toolType = ZaintToolKind.LINE
        onToolBarView().performSelectTool(toolType)
    }

    @Test
    fun testIfCurrentToolIsShownInBottomNavigation() {

        for (toolType in ZaintToolKind.values()) {
            val tool = toolType === ZaintToolKind.IMPORTPNG ||
                toolType === ZaintToolKind.COLORCHOOSER ||
                toolType === ZaintToolKind.REDO ||
                toolType === ZaintToolKind.UNDO ||
                toolType === ZaintToolKind.PIPETTE ||
                toolType === ZaintToolKind.LAYER
            if (tool) { continue }
            onToolBarView().performSelectTool(toolType)
            onBottomNavigationView().checkShowsCurrentTool(toolType)
        }
    }
}
