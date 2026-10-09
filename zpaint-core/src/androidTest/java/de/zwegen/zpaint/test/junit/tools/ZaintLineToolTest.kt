package de.zwegen.zpaint.test.junit.tools

import android.graphics.Paint
import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.test.annotation.UiThreadTest
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintLineTool
import de.zwegen.zpaint.tools.options.ZaintBrushOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.Perspective
import de.zwegen.zpaint.ui.viewholder.TopBarViewHolder
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class ZaintLineToolTest {
    private val toolPaint = Mockito.mock(ToolPaint::class.java)
    private val commandManager = Mockito.mock(ZaintCommandTimeline::class.java)
    private var workspace = Mockito.mock(Workspace::class.java)
    private val brushToolOptions = Mockito.mock(ZaintBrushOptions::class.java)
    private val toolOptionsViewController = Mockito.mock(ZaintToolOptionsController::class.java)
    private val contextCallback = Mockito.mock(ContextCallback::class.java)
    private lateinit var tool: ZaintLineTool
    private var screenWidth = 1920
    private var screenHeight = 1080
    private lateinit var idlingResource: CountingIdlingResource
    private val viewMock: View = Mockito.mock(View::class.java)

    @get:Rule
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    object MockitoHelper {
        fun <T> anyObject(): T {
            return Mockito.any<T>() ?: uninitialized()
        }
        @Suppress("UNCHECKED_CAST")
        fun <T> uninitialized(): T = null as T
    }

    @Before
    fun setUp() {
        idlingResource = launchActivityRule.activity.idlingResource
        IdlingRegistry.getInstance().register(idlingResource)
        Mockito.`when`(workspace.perspective).thenReturn(Perspective(screenWidth, screenHeight))
        Mockito.`when`(workspace.width).thenReturn(screenWidth)
        Mockito.`when`(workspace.height).thenReturn(screenHeight)
        Mockito.`when`(workspace.contains(MockitoHelper.anyObject()))
            .thenAnswer { invocation ->
                val point = invocation.getArgument<PointF>(0)
                point.x >= 0 && point.y >= 0 && point.x < screenWidth && point.y < screenHeight
            }
        val paint = Paint()
        Mockito.`when`(toolPaint.paint).thenReturn(paint)
        val topBarLayout = launchActivityRule.activity.findViewById<ViewGroup>(R.id.zpaint_layout_top_bar)
        ZaintLineTool.topBarViewHolder = TopBarViewHolder(topBarLayout)
        val plusButton: ImageButton = launchActivityRule.activity.findViewById(R.id.zpaint_btn_top_plus)
        ZaintLineTool.topBarViewHolder!!.plusButton = plusButton
        tool = ZaintLineTool(
            brushToolOptions,
            contextCallback,
            toolOptionsViewController,
            toolPaint,
            workspace,
            idlingResource,
            commandManager,
            0
        )

        Mockito.`when`(brushToolOptions!!.topOptions()).thenReturn(viewMock)
        Mockito.`when`(brushToolOptions.bottomOptions()).thenReturn(viewMock)
    }
    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    fun testInternalStateGetsResetWithPathOuterWorkspace() {
        tool.handleDown(PointF(-1f, -1f))
        tool.handleUp(PointF(-2f, -2f))

        Assert.assertEquals(tool.currentCoordinate, null)
        Assert.assertEquals(tool.initialEventCoordinate, null)
    }

    @Test
    fun testInternalStateGetsResetWithPathInWorkspace() {
        tool.handleDown(PointF(1f, 1f))
        tool.handleUp(PointF(2f, 2f))
        Assert.assertEquals(tool.currentCoordinate, null)
        Assert.assertEquals(tool.initialEventCoordinate, null)
    }

    @Test
    fun testInternalStateHandMoveNullPoint() {
        tool.handleDown(PointF(1f, 1f))
        tool.handleMove(null)
        Assert.assertEquals(tool.currentCoordinate, null)
        val point = PointF(2f, 2f)
        tool.handleMove(point)
        Assert.assertEquals(tool.currentCoordinate, point)
    }

    @Test
    @UiThreadTest
    fun testIfCheckmarkFeatureWorks() {
        tool.handleDown(PointF(1f, 1f))
        tool.handleUp(PointF(2f, 2f))
        Assert.assertEquals(tool.currentCoordinate, null)
        Assert.assertEquals(tool.initialEventCoordinate, null)
        Assert.assertEquals(tool.startpointSet, true)
        Assert.assertNotEquals(tool.startPointToDraw, null)
        tool.handleDown(PointF(5f, 5f))
        tool.handleUp(PointF(10f, 10f))
        Assert.assertEquals(tool.endpointSet, true)
        Assert.assertNotEquals(tool.endPointToDraw, null)
        tool.onClickOnButton()
        Assert.assertEquals(tool.lineFinalized, false)
    }

    @Test
    @UiThreadTest
    fun testIfPlusIsDisplayedAfterSettingSegment() {
        tool.handleDown(PointF(1f, 1f))
        tool.handleUp(PointF(2f, 2f))
        Assert.assertEquals(tool.currentCoordinate, null)
        Assert.assertEquals(tool.initialEventCoordinate, null)
        Assert.assertEquals(tool.startpointSet, true)
        Assert.assertNotEquals(tool.startPointToDraw, null)
        tool.handleDown(PointF(5f, 5f))
        tool.handleUp(PointF(10f, 10f))
        val plusButtonVisibility = ZaintLineTool.topBarViewHolder?.plusButton?.visibility
        Assert.assertEquals(plusButtonVisibility, View.VISIBLE)
        tool.onClickOnPlus()
        Assert.assertEquals(tool.connectedLines, true)
        Assert.assertEquals(tool.undoRecentlyClicked, false)
    }

    @Test
    @UiThreadTest
    fun testIfPlusIsDisplayedAfterDrawingLine() {
        tool.handleDown(PointF(1f, 1f))
        tool.handleMove(PointF(500f, 500f))
        tool.handleUp(PointF(500f, 500f))
        Assert.assertEquals(tool.currentCoordinate, null)
        Assert.assertEquals(tool.initialEventCoordinate, null)
        Assert.assertEquals(tool.startpointSet, true)
        Assert.assertNotEquals(tool.startPointToDraw, null)
        val plusButtonVisibility = ZaintLineTool.topBarViewHolder?.plusButton?.visibility
        Assert.assertEquals(plusButtonVisibility, View.VISIBLE)
        tool.onClickOnPlus()
        Assert.assertEquals(tool.connectedLines, true)
        Assert.assertEquals(tool.undoRecentlyClicked, false)
    }

    @Test
    fun testShouldCallHideWhenDrawing() {
        val tap1 = PointF(7f, 7f)
        Mockito.`when`(toolOptionsViewController.isVisible).thenReturn(true)
        Mockito.`when`(viewMock.visibility).thenReturn(View.VISIBLE)
        tool.handleMove(tap1, true)

        Mockito.verify(toolOptionsViewController).slideUp(viewMock,
                                                          willHide = true,
                                                          showOptionsView = false
        )
        Mockito.verify(toolOptionsViewController).slideDown(viewMock,
                                                            willHide = true,
                                                            showOptionsView = false
        )
    }

    @Test
    fun testShouldCallUnhideWhenDrawingFinish() {
        val tap1 = PointF(7f, 7f)
        Mockito.`when`(toolOptionsViewController.isVisible).thenReturn(false)
        Mockito.`when`(viewMock.visibility).thenReturn(View.INVISIBLE)
        tool.handleMove(tap1)
        tool.handleUp(tap1)

        Mockito.verify(toolOptionsViewController).slideDown(viewMock,
                                                            willHide = false,
                                                            showOptionsView = true
        )
        Mockito.verify(toolOptionsViewController).slideUp(viewMock,
                                                          willHide = false,
                                                          showOptionsView = true
        )
    }
}
