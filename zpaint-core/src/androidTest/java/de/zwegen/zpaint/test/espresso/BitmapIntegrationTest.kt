@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction.Companion.onToolBarView
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ZaintToolKind
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapIntegrationTest {
    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    @Before
    fun setUp() { onToolBarView().performSelectTool(ZaintToolKind.BRUSH) }

    @Test
    fun drawingSurfaceBitmapIsDisplaySize() {
        val activity = launchActivityRule.activity
        val workspace = activity.workspace
        val bitmapWidth = workspace.width
        val bitmapHeight = workspace.height
        val metrics = activity.resources.displayMetrics
        Assert.assertEquals(metrics.widthPixels.toLong(), bitmapWidth.toLong())
        Assert.assertEquals(metrics.heightPixels.toLong(), bitmapHeight.toLong())
    }
}
