@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso.tools

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.util.DrawingSurfaceLocationProvider
import de.zwegen.zpaint.test.espresso.util.UiInteractions
import de.zwegen.zpaint.test.espresso.util.wrappers.ClipboardToolViewInteraction.Companion.onClipboardToolViewInteraction
import de.zwegen.zpaint.test.espresso.util.wrappers.DrawingSurfaceInteraction.Companion.onDrawingSurfaceView
import de.zwegen.zpaint.test.espresso.util.wrappers.LayerMenuViewInteraction
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction.Companion.onToolBarView
import de.zwegen.zpaint.test.espresso.util.wrappers.TopBarViewInteraction.Companion.onTopBarView
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintRectangleToolBase
import de.zwegen.zpaint.tools.implementation.ZaintClipboardTool
import de.zwegen.zpaint.ui.Perspective
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZaintClipboardToolIntegrationTest {
    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()
    private var workspace: Workspace? = null
    private var perspective: Perspective? = null
    private var toolReference: ToolReference? = null
    private var mainActivity: ZaintEditorActivity? = null
    @Before
    fun setUp() {
        onToolBarView().performSelectTool(ZaintToolKind.BRUSH)
        mainActivity = launchActivityRule.activity
        workspace = mainActivity?.workspace
        perspective = mainActivity?.perspective
        toolReference = mainActivity?.toolReference
    }

    @Test
    fun testCopyPixel() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onToolBarView().performSelectTool(ZaintToolKind.CLIPBOARD)
        onClipboardToolViewInteraction().performCopy()
        val stampTool = toolReference?.tool as ZaintClipboardTool?
        stampTool?.toolPosition?.set(stampTool.toolPosition.x, stampTool.toolPosition.y * .5f)
        onClipboardToolViewInteraction().performPaste()
        stampTool?.toolPosition?.let {
            onDrawingSurfaceView()
                .checkPixelColor(Color.BLACK, stampTool.toolPosition.x, it.y)
        }
    }

    @Test
    fun testCutAndPastePixel() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onToolBarView()
            .performSelectTool(ZaintToolKind.CLIPBOARD)
        onClipboardToolViewInteraction()
            .performCut()
        val stampTool = toolReference?.tool as ZaintClipboardTool?
        stampTool?.toolPosition?.let {
            onDrawingSurfaceView()
                .checkPixelColor(
                    Color.TRANSPARENT,
                    stampTool.toolPosition.x,
                    it.y
                )
        }
        onClipboardToolViewInteraction().performPaste()
        stampTool?.toolPosition?.x?.let {
            onDrawingSurfaceView()
                .checkPixelColor(Color.BLACK, it, stampTool.toolPosition.y)
        }
    }

    @Test
    fun testStampToolNotCapturingOtherLayers() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onToolBarView()
            .performSelectTool(ZaintToolKind.CLIPBOARD)
        LayerMenuViewInteraction.onLayerMenuView()
            .performOpen()
            .performAddLayer()
        LayerMenuViewInteraction.onLayerMenuView()
            .performClose()
        onClipboardToolViewInteraction()
            .performCopy()
        val stampTool = toolReference?.tool as ZaintClipboardTool?
        stampTool!!.toolPosition[stampTool.toolPosition.x] = stampTool.toolPosition.y * .5f
        onClipboardToolViewInteraction()
            .performPaste()
        onDrawingSurfaceView()
            .checkPixelColor(
                Color.TRANSPARENT,
                stampTool.toolPosition.x,
                stampTool.toolPosition.y * .5f
            )
    }

    @Test
    fun testStampOutsideDrawingSurface() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        val bitmapWidth = workspace?.width
        val bitmapHeight = workspace?.height
        perspective?.scale = SCALE_25
        onToolBarView()
            .performSelectTool(ZaintToolKind.CLIPBOARD)
        val stampTool = toolReference?.tool as ZaintClipboardTool?
        val toolPosition = perspective?.surfaceCenterX?.let {
            perspective?.surfaceCenterY?.let {
                    it1 ->
                PointF(it, it1)
            }
        }
        if (toolPosition != null) {
            stampTool?.toolPosition?.set(toolPosition)
        }
        if (bitmapWidth != null) {
            stampTool?.boxWidth = bitmapWidth * STAMP_RESIZE_FACTOR
        }
        if (bitmapHeight != null) {
            stampTool?.boxHeight = bitmapHeight * STAMP_RESIZE_FACTOR
        }
        onClipboardToolViewInteraction().performPaste()
        Assert.assertNotNull(stampTool?.drawingBitmap)
    }

    @Test
    fun testBitmapSavedOnOrientationChange() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onToolBarView()
            .performSelectTool(ZaintToolKind.CLIPBOARD)
        val emptyBitmap =
            (toolReference?.tool as ZaintRectangleToolBase?)?.drawingBitmap?.let {
                Bitmap.createBitmap(it)
            }
        onClipboardToolViewInteraction().performCopy()
        val expectedBitmap =
            (toolReference?.tool as ZaintRectangleToolBase?)?.drawingBitmap?.let {
                Bitmap.createBitmap(it)
            }
        if (expectedBitmap != null) {
            Assert.assertFalse(expectedBitmap.sameAs(emptyBitmap))
        }
        mainActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val actualBitmap =
            (toolReference?.tool as ZaintRectangleToolBase?)?.drawingBitmap?.let {
                Bitmap.createBitmap(it)
            }
        if (expectedBitmap != null) {
            Assert.assertTrue(expectedBitmap.sameAs(actualBitmap))
        }
    }

    @Test
    fun testStampToolDoesNotResetPerspectiveScale() {
        val scale = 2.0f
        perspective?.scale = scale
        perspective?.surfaceTranslationX = 50F
        perspective?.surfaceTranslationY = 200F
        mainActivity?.refreshDrawingSurface()
        onToolBarView().performSelectTool(ZaintToolKind.CLIPBOARD)
        perspective?.scale?.let { Assert.assertEquals(scale, it, 0.0001f) }
    }

    companion object {
        private const val SCALE_25 = 0.25f
        private const val STAMP_RESIZE_FACTOR = 1.5f
    }
}
