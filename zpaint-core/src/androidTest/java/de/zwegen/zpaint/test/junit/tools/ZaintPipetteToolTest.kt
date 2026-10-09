package de.zwegen.zpaint.test.junit.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintPipetteTool
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import org.hamcrest.Matchers
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert

@RunWith(MockitoJUnitRunner.Silent::class)
class ZaintPipetteToolTest {
    @Mock
    private val commandManager: ZaintCommandTimeline? = null

    @Mock
    private val listener: OnColorPickedListener? = null

    @Mock
    private val toolPaint: ToolPaint? = null

    @Mock
    private val workspace: Workspace? = null

    @Mock
    private val toolOptionsViewController: ZaintToolOptionsController? = null

    @Mock
    private val contextCallback: ContextCallback? = null
    private var toolToTest: ZaintPipetteTool? = null
    private var idlingResource: CountingIdlingResource? = null
    private var mainActivity: ZaintEditorActivity? = null

    @Rule
    @JvmField
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)
    @Before
    fun setUp() {
        mainActivity = launchActivityRule.activity
        idlingResource = launchActivityRule.activity.idlingResource
        IdlingRegistry.getInstance().register(idlingResource)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(X_COORDINATE_RED, 0, Color.RED)
        bitmap.setPixel(X_COORDINATE_GREEN, 0, Color.GREEN)
        bitmap.setPixel(X_COORDINATE_BLUE, 0, Color.BLUE)
        bitmap.setPixel(X_COORDINATE_PART_TRANSPARENT, 0, -0x55555556)
        Mockito.`when`(workspace!!.bitmapOfAllLayers).thenReturn(bitmap)
        Mockito.`when`(workspace.contains(any<PointF>())).thenAnswer { invocation ->
            val argument = invocation.getArgument<PointF>(0)
            argument.x >= 0 && argument.y >= 0 && argument.x < 10 && argument.y < 10
        }
        toolToTest = ZaintPipetteTool(contextCallback!!, toolOptionsViewController!!, toolPaint!!,
            workspace, idlingResource!!, commandManager!!, listener!!, mainActivity!!,
            addToColorHistory = { color -> mainActivity!!.model.colorHistory.addColor(color) })
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    fun testHandleDown() {
        toolToTest!!.handleDown(PointF(X_COORDINATE_RED.toFloat(), 0f))
        toolToTest!!.handleMove(PointF(X_COORDINATE_PART_TRANSPARENT.toFloat(), 0f))
        val inOrderToolPaint = Mockito.inOrder(toolPaint)
        inOrderToolPaint.verify(toolPaint)!!.color = Color.RED
        inOrderToolPaint.verify(toolPaint)!!.color = -0x55555556
        val inOrderListener = Mockito.inOrder(listener)
        inOrderListener.verify(listener)!!.colorChanged(Color.RED)
        inOrderListener.verify(listener)!!.colorChanged(-0x55555556)
    }

    @Test
    fun testHandleMove() {
        toolToTest!!.handleDown(PointF(X_COORDINATE_RED.toFloat(), 0f))
        toolToTest!!.handleMove(PointF((X_COORDINATE_RED + 1).toFloat(), 0f))
        toolToTest!!.handleMove(PointF(X_COORDINATE_GREEN.toFloat(), 0f))
        toolToTest!!.handleMove(PointF(X_COORDINATE_PART_TRANSPARENT.toFloat(), 0f))
        val inOrderToolPaint = Mockito.inOrder(toolPaint)
        inOrderToolPaint.verify(toolPaint)!!.color = Color.RED
        inOrderToolPaint.verify(toolPaint)!!.color = Color.TRANSPARENT
        inOrderToolPaint.verify(toolPaint)!!.color = Color.GREEN
        inOrderToolPaint.verify(toolPaint)!!.color = -0x55555556
        val inOrderListener = Mockito.inOrder(listener)
        inOrderListener.verify(listener)!!.colorChanged(Color.RED)
        inOrderListener.verify(listener)!!.colorChanged(Color.TRANSPARENT)
        inOrderListener.verify(listener)!!.colorChanged(Color.GREEN)
        inOrderListener.verify(listener)!!.colorChanged(-0x55555556)
    }

    @Test
    fun testHandleUp() {
        toolToTest!!.handleUp(PointF(X_COORDINATE_BLUE.toFloat(), 0f))
        toolToTest!!.handleUp(PointF(X_COORDINATE_PART_TRANSPARENT.toFloat(), 0f))
        val inOrderToolPaint = Mockito.inOrder(toolPaint)
        inOrderToolPaint.verify(toolPaint)!!.color = Color.BLUE
        inOrderToolPaint.verify(toolPaint)!!.color = -0x55555556
        val inOrderListener = Mockito.inOrder(listener)
        inOrderListener.verify(listener)!!.colorChanged(Color.BLUE)
        inOrderListener.verify(listener)!!.colorChanged(-0x55555556)
    }

    @Test
    fun testShouldReturnCorrectToolType() {
        Assert.assertThat(toolToTest!!.toolType, Matchers.`is`(ZaintToolKind.PIPETTE))
    }

    @Test
    fun testShouldReturnCorrectColorForForTopButtonIfColorIsTransparent() {
        toolToTest!!.handleUp(PointF(0f, 0f))
        Mockito.verify(toolPaint)!!.color = Color.TRANSPARENT
    }

    @Test
    fun testShouldReturnCorrectColorForForTopButtonIfColorIsRed() {
        toolToTest!!.handleUp(PointF(X_COORDINATE_RED.toFloat(), 0f))
        Mockito.verify(toolPaint)!!.color = Color.RED
    }

    companion object {
        private const val X_COORDINATE_RED = 1
        private const val X_COORDINATE_GREEN = 3
        private const val X_COORDINATE_BLUE = 5
        private const val X_COORDINATE_PART_TRANSPARENT = 7
    }
}
