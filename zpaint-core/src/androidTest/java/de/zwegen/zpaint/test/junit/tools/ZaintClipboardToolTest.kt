package de.zwegen.zpaint.test.junit.tools

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Looper
import android.util.DisplayMetrics
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.mockito.kotlin.any
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintClipboardTool
import de.zwegen.zpaint.tools.options.ZaintClipboardOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.Perspective
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ZaintClipboardToolTest {
    @Mock
    private val toolPaint: ToolPaint? = null

    @Mock
    private val commandManager: ZaintCommandTimeline? = null

    @Mock
    private val workspace: Workspace? = null

    @Mock
    private val clipboardToolOptionsView: ZaintClipboardOptions? = null

    @Mock
    private val toolOptionsViewController: ZaintToolOptionsController? = null

    @Mock
    private val contextCallback: ContextCallback? = null

    @Mock
    private val displayMetrics: DisplayMetrics? = null
    private var tool: ZaintClipboardTool? = null
    private var idlingResource: CountingIdlingResource? = null

    private val viewMock = Mockito.mock(ViewGroup::class.java)
    @Rule
    @JvmField
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    @Before
    fun setUp() {
        idlingResource = launchActivityRule.activity.idlingResource
        IdlingRegistry.getInstance().register(idlingResource)
        Mockito.`when`(contextCallback!!.displayMetrics).thenReturn(displayMetrics)
        displayMetrics!!.widthPixels = 200
        displayMetrics.heightPixels = 300
        Mockito.`when`(workspace!!.scale).thenReturn(1f)
        Mockito.`when`(workspace.width).thenReturn(200)
        Mockito.`when`(workspace.height).thenReturn(300)
        Mockito.`when`(workspace.perspective).thenReturn(Perspective(200, 300))
        Mockito.`when`(workspace.getCanvasPointFromSurfacePoint(any<PointF>()))
            .then { invocation -> invocation.getArgument<PointF>(0) }
        tool = ZaintClipboardTool(
            clipboardToolOptionsView!!,
            contextCallback,
            toolOptionsViewController!!,
            toolPaint!!,
            workspace,
            idlingResource!!,
            commandManager!!,
            0
        )
        Mockito.`when`(toolOptionsViewController.toolSpecificOptionsLayout).thenReturn(viewMock)
        Mockito.`when`(toolOptionsViewController.toolSpecificOptionsLayout).thenReturn(viewMock)
        Mockito.`when`(clipboardToolOptionsView.getClipboardToolOptionsLayout()).thenReturn(viewMock)
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    @Throws(InterruptedException::class)
    fun testLongClickResetsToolPosition() {
        Mockito.`when`(workspace!!.bitmapOfCurrentLayer).thenReturn(
            Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        )
        val initialX = tool!!.toolPosition.x
        val initialY = tool!!.toolPosition.y
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            tool!!.handleDown(PointF(initialX, initialY))
            tool!!.handleMove(PointF(initialX + 2, initialY + 3))
        }
        Assert.assertEquals(initialX + 2, tool!!.toolPosition.x, Float.MIN_VALUE)
        Assert.assertEquals(initialY + 3, tool!!.toolPosition.y, Float.MIN_VALUE)
        Thread.sleep((ViewConfiguration.getLongPressTimeout() + 50).toLong())
        Assert.assertEquals(initialX + 2, tool!!.toolPosition.x, Float.MIN_VALUE)
        Assert.assertEquals(initialY + 3, tool!!.toolPosition.y, Float.MIN_VALUE)
    }

    @Test
    fun testShouldReturnCorrectToolType() {
        Assert.assertEquals(ZaintToolKind.CLIPBOARD, tool!!.toolType)
    }

    @Test
    fun testToolPreciseMovementTest() {
        Looper.prepare()
        val initialToolPositionX = tool!!.toolPosition.x
        val initialToolPositionY = tool!!.toolPosition.y
        tool!!.handleDown(PointF(initialToolPositionX, initialToolPositionY))
        tool!!.handleMove(PointF(initialToolPositionX + 9, initialToolPositionY + 9))
        tool!!.handleUp(PointF(initialToolPositionX + 9, initialToolPositionY + 9))
        Assert.assertEquals(tool!!.toolPosition.x, initialToolPositionX + 9, 0f)
        Assert.assertEquals(tool!!.toolPosition.y, initialToolPositionY + 9, 0f)
    }
}
