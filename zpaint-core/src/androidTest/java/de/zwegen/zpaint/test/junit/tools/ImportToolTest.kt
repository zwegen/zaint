@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.junit.tools

import org.junit.runner.RunWith
import org.mockito.Mock
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.tools.ContextCallback
import android.util.DisplayMetrics
import de.zwegen.zpaint.tools.implementation.ZaintImportTool
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import androidx.test.annotation.UiThreadTest
import org.junit.Before
import androidx.test.espresso.IdlingRegistry
import org.mockito.Mockito
import de.zwegen.zpaint.ui.Perspective
import android.graphics.Bitmap
import de.zwegen.zpaint.tools.implementation.DEFAULT_BOX_RESIZE_MARGIN
import de.zwegen.zpaint.tools.implementation.MAXIMUM_BORDER_RATIO
import de.zwegen.zpaint.tools.options.ImportToolOptionsView
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class ImportToolTest {
    @Mock
    private lateinit var commandManager: ZaintCommandTimeline

    @Mock
    private lateinit var workspace: Workspace

    @Mock
    private lateinit var toolPaint: ToolPaint

    @Mock
    private lateinit var toolOptionsViewController: ZaintToolOptionsController

    @Mock
    private lateinit var contextCallback: ContextCallback

    @Mock
    private val importToolOptionsView: ImportToolOptionsView? = null

    @Mock
    private val displayMetrics: DisplayMetrics? = null
    private var drawingSurfaceWidth = 0
    private var drawingSurfaceHeight = 0
    private lateinit var tool: ZaintImportTool
    private lateinit var idlingResource: CountingIdlingResource

    @Rule
    @JvmField
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @UiThreadTest
    @Before
    fun setUp() {
        idlingResource = launchActivityRule.activity.idlingResource
        IdlingRegistry.getInstance().register(idlingResource)
        drawingSurfaceWidth = 20
        drawingSurfaceHeight = 30
        displayMetrics?.heightPixels = 1920
        displayMetrics?.widthPixels = 1080
        displayMetrics?.density = 1f
        Mockito.`when`(contextCallback.displayMetrics).thenReturn(displayMetrics)
        Mockito.`when`(workspace.width).thenReturn(20)
        Mockito.`when`(workspace.height).thenReturn(30)
        Mockito.`when`(workspace.scale).thenReturn(1f)
        Mockito.`when`(workspace.perspective).thenReturn(Perspective(20, 30))
        tool = ZaintImportTool(importToolOptionsView!!, contextCallback, toolOptionsViewController, toolPaint, workspace,
                          idlingResource, commandManager, 0)
    }

    @After
    fun tearDown() { IdlingRegistry.getInstance().unregister(idlingResource) }

    @Test
    fun testImport() {
        val width = drawingSurfaceWidth
        val height = drawingSurfaceHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        tool.setBitmapFromSource(bitmap)
        tool.boxWidth.let { Assert.assertEquals(width.toFloat(), it, Float.MIN_VALUE) }
        tool.boxHeight.let { Assert.assertEquals(height.toFloat(), it, Float.MIN_VALUE) }
    }

    @Test
    fun testImportTooSmall() {
        val width = 1
        val height = 1
        val minSize: Int = DEFAULT_BOX_RESIZE_MARGIN
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        tool.setBitmapFromSource(bitmap)
        tool.boxWidth.let { Assert.assertEquals(minSize.toFloat(), it, Float.MIN_VALUE) }
        tool.boxHeight.let { Assert.assertEquals(minSize.toFloat(), it, Float.MIN_VALUE) }
    }

    @Test
    fun testImportTooLarge() {
        val width = (drawingSurfaceWidth * MAXIMUM_BORDER_RATIO).toInt()
        val height = (drawingSurfaceHeight * MAXIMUM_BORDER_RATIO).toInt()
        val bitmap = Bitmap.createBitmap(width + 1, height + 1, Bitmap.Config.ARGB_8888)
        tool.setBitmapFromSource(bitmap)
        tool.boxWidth.let { Assert.assertEquals(width.toFloat(), it, Float.MIN_VALUE) }
        tool.let { Assert.assertEquals(height.toFloat(), it.boxHeight, Float.MIN_VALUE) }
    }

    @Test
    fun testImportGetsDownscaledWhenNotEnoughMemory() {
        drawingSurfaceWidth = 1080
        drawingSurfaceHeight = 1920
        Mockito.`when`(workspace.height).thenReturn(1920)
        Mockito.`when`(workspace.width).thenReturn(1080)
        val width = drawingSurfaceWidth * 8
        val height = drawingSurfaceHeight * 8
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        tool.setBitmapFromSource(bitmap)
        Assert.assertTrue(tool.boxHeight < height)
        Assert.assertTrue(tool.boxWidth < width)
    }
}
