package de.zwegen.zpaint.test.junit.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintSmudgeStroke
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintSmudgeTool
import de.zwegen.zpaint.tools.options.ZaintSmudgeOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class ZaintSmudgeToolTest {
    private val toolPaint = Mockito.mock(ToolPaint::class.java)
    private val commandManager = Mockito.mock(ZaintCommandTimeline::class.java)
    private val workspace = Mockito.mock(Workspace::class.java)
    private val smudgeToolOptionsView = Mockito.mock(ZaintSmudgeOptions::class.java)
    private val toolOptionsViewController = Mockito.mock(ZaintToolOptionsController::class.java)
    private val contextCallback = Mockito.mock(ContextCallback::class.java)
    private lateinit var idlingResource: CountingIdlingResource
    private lateinit var tool: ZaintSmudgeTool

    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @Before
    fun setUp() {
        idlingResource = launchActivityRule.getActivity().idlingResource
        IdlingRegistry.getInstance().register(idlingResource)
        Mockito.`when`(toolPaint.strokeWidth).thenReturn(100f)
        Mockito.`when`(toolPaint.strokeCap).thenReturn(Paint.Cap.ROUND)
        Mockito.`when`(workspace.width).thenReturn(200)
        Mockito.`when`(workspace.height).thenReturn(200)
        Mockito.`when`(workspace.surfaceWidth).thenReturn(200)
        Mockito.`when`(workspace.surfaceHeight).thenReturn(200)
        Mockito.`when`(workspace.getCanvasPointFromSurfacePoint(Mockito.any(PointF::class.java)))
            .thenReturn(PointF(100f, 100f))

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(100, 100, Color.GREEN)
        Mockito.`when`(workspace.bitmapOfCurrentLayer).thenReturn(bitmap)

        tool = ZaintSmudgeTool(
            smudgeToolOptionsView,
            contextCallback,
            toolOptionsViewController,
            toolPaint,
            workspace,
            idlingResource,
            commandManager
        )
    }

    @After
    fun tearDown() {
        tool.resetInternalState(Tool.StateChange.ALL)
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    fun testShouldReturnCorrectToolType() {
        Assert.assertEquals(ZaintToolKind.SMUDGE, tool.toolType)
    }

    @Test
    fun testNotExecutingSmudgeWhenBitmapHasNoColor() {
        Mockito.`when`(workspace.bitmapOfCurrentLayer)
            .thenReturn(Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888))
        val event = PointF(100f, 100f)
        Assert.assertFalse(tool.handleDown(event))
        Assert.assertFalse(tool.handleMove(event))
        Assert.assertFalse(tool.handleUp(event))
    }

    @Test
    fun testExecutingSmudgeWhenBitmapHasColor() {
        val event = PointF(100f, 100f)
        Assert.assertTrue(tool.handleDown(event))
        Assert.assertTrue(tool.handleMove(event))
        Assert.assertTrue(tool.handleUp(event))
    }

    @Test
    fun testSmudgePathCommandCreated() {
        val event = PointF(100f, 100f)
        tool.handleDown(event)
        val event2 = PointF(110f, 110f)
        tool.handleMove(event2)
        tool.handleUp(event2)
        val argument = ArgumentCaptor.forClass(ZaintSmudgeStroke::class.java)
        Mockito.verify(commandManager).addCommand(argument.capture())
    }

    @Test
    fun testPointsArrayIsClearedAfterCommandRun() {
        val event = PointF(100f, 100f)
        tool.handleDown(event)
        val event2 = PointF(110f, 110f)
        tool.handleMove(event2)
        tool.handleUp(event2)
        Assert.assertTrue(tool.pointArray.isEmpty())
    }

    @Test
    fun testPressureUpdatedCorrectly() {
        tool.updatePressure(40)
        val pressure = tool.maxPressure
        tool.updatePressure(20)
        Assert.assertNotEquals(pressure, tool.maxPressure)
    }

    @Test
    fun testDragUpdatedCorrectly() {
        tool.updateDrag(80)
        val minSize = tool.minSmudgeSize
        tool.updateDrag(40)
        Assert.assertNotEquals(minSize, tool.minSmudgeSize)
    }

    @Test
    fun testSmudgeSizeScalesFromOneToTwentyPercentOfShorterImageSide() {
        Mockito.`when`(workspace.width).thenReturn(1000)
        Mockito.`when`(workspace.height).thenReturn(600)

        tool.changePaintStrokeWidth(1)
        Assert.assertEquals(6f, tool.maxSmudgeSize)

        tool.changePaintStrokeWidth(100)
        Assert.assertEquals(120f, tool.maxSmudgeSize)
    }

    @Test
    fun testSizePreviewUsesVisibleSurfaceCenter() {
        Mockito.`when`(workspace.surfaceWidth).thenReturn(1080)
        Mockito.`when`(workspace.surfaceHeight).thenReturn(1920)
        Mockito.`when`(workspace.getCanvasPointFromSurfacePoint(Mockito.any(PointF::class.java)))
            .thenReturn(PointF(500f, 750f))

        tool.changePaintStrokeWidth(80)

        val centerCaptor = ArgumentCaptor.forClass(PointF::class.java)
        Mockito.verify(workspace).getCanvasPointFromSurfacePoint(centerCaptor.capture())
        Assert.assertEquals(540f, centerCaptor.value.x)
        Assert.assertEquals(960f, centerCaptor.value.y)
    }
}
