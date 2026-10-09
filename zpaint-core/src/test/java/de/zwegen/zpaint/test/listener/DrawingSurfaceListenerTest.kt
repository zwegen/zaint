package de.zwegen.zpaint.test.listener

import android.graphics.PointF
import android.view.MotionEvent
import de.zwegen.zpaint.listener.DrawingSurfaceListener
import de.zwegen.zpaint.listener.DrawingSurfaceListener.DrawingSurfaceListenerCallback
import de.zwegen.zpaint.test.utils.PointFMatcher.Companion.pointFEquals
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.ZaintDrawingSurface
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class DrawingSurfaceListenerTest {
    @Mock
    private val currentTool: Tool? = null

    @Mock
    private val toolOptionsViewController: ZaintToolOptionsController? = null

    @Mock
    private val callback: DrawingSurfaceListenerCallback? = null

    @Mock
    private val motionEvent: MotionEvent? = null

    private var drawingSurfaceListener: DrawingSurfaceListener? = null
    private val initalPositionX = 41f
    private val initalPositionY = 1f
    private val firstMovementPositionX = 5f
    private val firstMovementPositionY = 5f
    private val secondMovementPositionX = 6f
    private val secondMovementPositionY = 6f
    private val actionUpMovementPositionX = 7f
    private val actionUpMovementPositionY = 7f
    private val width = 97
    private val height = 11
    private val firstMovementTimestamp: Long = 150
    private val secondMovementTimestamp: Long = 170
    private val actionUpTimestamp: Long = 175
    @Before
    fun setUp() {
        Mockito.`when`(callback!!.getCurrentTool())
            .thenReturn(currentTool)
        Mockito.`when`(callback.getZaintToolOptionsController())
            .thenReturn(toolOptionsViewController)
        Mockito.`when`(motionEvent!!.downTime).thenReturn(0L)
        drawingSurfaceListener = DrawingSurfaceListener(callback, DISPLAY_DENSITY)
    }

    @Test
    fun testSetUp() {
        Mockito.verifyNoInteractions(currentTool, callback)
    }

    private fun triggerTouchDownEvent(
        startPositionX: Float,
        startPositionY: Float,
        drawingSurface: ZaintDrawingSurface
    ) {
        Mockito.`when`(motionEvent!!.actionMasked).thenReturn(MotionEvent.ACTION_DOWN)
        Mockito.`when`(motionEvent.x).thenReturn(startPositionX)
        Mockito.`when`(motionEvent.y).thenReturn(startPositionY)
        Mockito.`when`(drawingSurface.width).thenReturn(width)
        Mockito.`when`(drawingSurface.height).thenReturn(height)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
    }

    private fun triggerMovementEvent(
        positionX: Float,
        positionY: Float,
        drawingSurface: ZaintDrawingSurface
    ) {
        Mockito.`when`(motionEvent!!.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.x).thenReturn(positionX)
        Mockito.`when`(motionEvent.y).thenReturn(positionY)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(1)
        Mockito.`when`(drawingSurface.width).thenReturn(width)
        Mockito.`when`(drawingSurface.height).thenReturn(height)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
    }

    private fun triggerMovementEventWithTimestamp(
        positionX: Float,
        positionY: Float,
        drawingSurface: ZaintDrawingSurface,
        timestamp: Long
    ) {
        Mockito.`when`(motionEvent!!.eventTime).thenReturn(timestamp)
        triggerMovementEvent(positionX, positionY, drawingSurface)
    }

    fun triggerTouchUpEvent(timestamp: Long, drawingSurface: ZaintDrawingSurface) {
        Mockito.`when`(motionEvent!!.eventTime).thenReturn(timestamp)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_UP)
        Mockito.`when`(motionEvent.x).thenReturn(actionUpMovementPositionX)
        Mockito.`when`(motionEvent.y).thenReturn(actionUpMovementPositionY)
        Mockito.`when`(drawingSurface.width).thenReturn(width)
        Mockito.`when`(drawingSurface.height).thenReturn(height)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
    }

    @Test
    fun testOnTouchDown() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        triggerTouchDownEvent(41f, 5f, drawingSurface)
        Mockito.verify<DrawingSurfaceListenerCallback?>(callback)
            .convertToCanvasFromSurface(pointFEquals(41f, 5f))
        Mockito.verify<Tool?>(currentTool).handleDown(pointFEquals(41f, 5f))
        Mockito.verify(callback, Mockito.never())
            ?.multiplyPerspectiveScale(ArgumentMatchers.anyFloat())
        Mockito.verify(callback, Mockito.never())
            ?.translatePerspective(ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
    }

    @Test
    fun hidesVisibleContextOptionsUntilCanvasTouchEnds() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        Mockito.`when`(toolOptionsViewController!!.isVisible).thenReturn(true)

        triggerTouchDownEvent(41f, 5f, drawingSurface)
        triggerTouchUpEvent(actionUpTimestamp, drawingSurface)

        Mockito.verify(toolOptionsViewController)?.hide()
        Mockito.verify(toolOptionsViewController)?.show()
    }

    @Test
    fun secondFingerCancelsTheActiveToolBeforePinching() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        Mockito.`when`(motionEvent!!.actionMasked).thenReturn(MotionEvent.ACTION_POINTER_DOWN)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2)
        Mockito.`when`(motionEvent.getX(0)).thenReturn(10f)
        Mockito.`when`(motionEvent.getY(0)).thenReturn(10f)
        Mockito.`when`(motionEvent.getX(1)).thenReturn(30f)
        Mockito.`when`(motionEvent.getY(1)).thenReturn(30f)

        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)

        Mockito.verify(currentTool)?.resetInternalState(StateChange.MOVE_CANCELED)
        Mockito.verify(callback, Mockito.never())?.multiplyPerspectiveScale(ArgumentMatchers.anyFloat())
    }

    @Test
    fun testOnTouchDownIgnoredIfInsideDrawerLeftEdge() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_DOWN)
        Mockito.`when`(motionEvent.x).thenReturn(20 * DISPLAY_DENSITY - 1)
        Mockito.`when`(motionEvent.y).thenReturn(5f)
        val onTouchResult = drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Assert.assertFalse(onTouchResult)
    }

    @Test
    fun testOnTouchDownIgnoredIfInsideDrawerRightEdge() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_DOWN)
        Mockito.`when`(motionEvent.x).thenReturn(67f)
        Mockito.`when`(motionEvent.y).thenReturn(5f)
        Mockito.`when`(drawingSurface.width).thenReturn((67 + 20 * DISPLAY_DENSITY - 1).toInt())
        val onTouchResult = drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Assert.assertFalse(onTouchResult)
    }

    @Test
    fun testOnTouchMoveInDrawMode() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        triggerMovementEvent(5f, 3f, drawingSurface)
        Mockito.verify<DrawingSurfaceListenerCallback?>(callback)
            .convertToCanvasFromSurface(pointFEquals(5f, 3f))
    }

    @Test
    fun remainingFingerDoesNotResumeDrawingAfterPinch() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.x).thenReturn(5f)
        Mockito.`when`(motionEvent.y).thenReturn(3f)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2, 1)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify(currentTool, Mockito.never())?.handleMove(
            ArgumentMatchers.any(
                PointF::class.java
            ),
            ArgumentMatchers.any(
                Boolean::class.java
            )
            )
    }

    @Test
    fun finishingAPinchDoesNotSendAToolUpEvent() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2)
        Mockito.`when`(motionEvent.getX(0)).thenReturn(10f)
        Mockito.`when`(motionEvent.getY(0)).thenReturn(10f)
        Mockito.`when`(motionEvent.getX(1)).thenReturn(30f)
        Mockito.`when`(motionEvent.getY(1)).thenReturn(30f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)

        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_UP)
        Mockito.`when`(motionEvent.x).thenReturn(actionUpMovementPositionX)
        Mockito.`when`(motionEvent.y).thenReturn(actionUpMovementPositionY)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)

        Mockito.verify(currentTool, Mockito.never())?.handleUp(
            ArgumentMatchers.any(PointF::class.java)
        )
        Mockito.verify(currentTool, Mockito.atLeastOnce())?.resetInternalState(StateChange.MOVE_CANCELED)
    }

    @Test
    fun testOnTouchMoveInPinchMode() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.x).thenReturn(7f)
        Mockito.`when`(motionEvent.y).thenReturn(11f)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify(currentTool, Mockito.never())?.handleMove(
            ArgumentMatchers.any(
                PointF::class.java
            ),
            ArgumentMatchers.any(
                Boolean::class.java
            )
        )
        Mockito.verify(callback, Mockito.atLeastOnce())
            ?.translatePerspective(ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
    }

    @Test
    fun testOnTouchMoveInHandMode() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.x).thenReturn(7f)
        Mockito.`when`(motionEvent.y).thenReturn(11f)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(1)
        Mockito.`when`(currentTool!!.handToolMode()).thenReturn(true)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify(currentTool, Mockito.never()).handleUp(
            ArgumentMatchers.any(
                PointF::class.java
            )
        )
        Mockito.verify(currentTool, Mockito.never()).handleDown(
            ArgumentMatchers.any(
                PointF::class.java
            )
        )
        Mockito.verify(currentTool, Mockito.never()).handleMove(
            ArgumentMatchers.any(
                PointF::class.java
            ),
            ArgumentMatchers.any(
                Boolean::class.java
            )
        )
        Mockito.verify(callback, Mockito.atLeastOnce())
            ?.translatePerspective(ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
        Mockito.verify(callback, Mockito.never())
            ?.multiplyPerspectiveScale(ArgumentMatchers.anyFloat())
    }

    @Test
    fun testOnTouchMoveInPinchModeStopsAutoScroll() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2)
        Mockito.`when`(motionEvent.x).thenReturn(50f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
    }

    @Test
    fun testOnTouchMoveInHandModeStopsAutoScroll() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(1)
        Mockito.`when`(currentTool!!.handToolMode()).thenReturn(true)
        Mockito.`when`(motionEvent.x).thenReturn(50f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
    }

    @Test
    fun testOnTouchMoveTranslateInPinchMode() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.getX(0)).thenReturn(7f)
        Mockito.`when`(motionEvent.getY(0)).thenReturn(11f)
        Mockito.`when`(motionEvent.getX(1)).thenReturn(23f)
        Mockito.`when`(motionEvent.getY(1)).thenReturn(29f)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.`when`(motionEvent.getX(0)).thenReturn(17f)
        Mockito.`when`(motionEvent.getY(0)).thenReturn(1f)
        Mockito.`when`(motionEvent.getX(1)).thenReturn(33f)
        Mockito.`when`(motionEvent.getY(1)).thenReturn(19f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify(currentTool, Mockito.never())?.handleMove(
            ArgumentMatchers.any(
                PointF::class.java
            ),
            ArgumentMatchers.any(
                Boolean::class.java
            )
        )
        Mockito.verify(callback, Mockito.atLeastOnce())
            ?.translatePerspective(ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
    }

    @Test
    fun testOnTouchMoveTranslateInHandMode() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.x).thenReturn(7f)
        Mockito.`when`(motionEvent.y).thenReturn(11f)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(1)
        Mockito.`when`(currentTool!!.handToolMode()).thenReturn(true)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.`when`(motionEvent.x).thenReturn(17f)
        Mockito.`when`(motionEvent.y).thenReturn(1f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify(currentTool, Mockito.never()).handleUp(
            ArgumentMatchers.any(
                PointF::class.java
            )
        )
        Mockito.verify(currentTool, Mockito.never()).handleDown(
            ArgumentMatchers.any(
                PointF::class.java
            )
        )
        Mockito.verify(currentTool, Mockito.never()).handleMove(
            ArgumentMatchers.any(
                PointF::class.java
            ),
            ArgumentMatchers.any(
                Boolean::class.java
            )
        )
        Mockito.verify(callback, Mockito.atLeastOnce())
            ?.translatePerspective(ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
        Mockito.verify(callback, Mockito.never())
            ?.multiplyPerspectiveScale(ArgumentMatchers.anyFloat())
    }

    @Test
    fun testOnTouchMoveScaleInPinchMode() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_MOVE)
        Mockito.`when`(motionEvent.getX(0)).thenReturn(2f)
        Mockito.`when`(motionEvent.getY(0)).thenReturn(2f)
        Mockito.`when`(motionEvent.getX(1)).thenReturn(4f)
        Mockito.`when`(motionEvent.getY(1)).thenReturn(4f)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.`when`(motionEvent.getX(0)).thenReturn(1f)
        Mockito.`when`(motionEvent.getY(0)).thenReturn(1f)
        Mockito.`when`(motionEvent.getX(1)).thenReturn(5f)
        Mockito.`when`(motionEvent.getY(1)).thenReturn(5f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.`when`(motionEvent.getX(0)).thenReturn(2f)
        Mockito.`when`(motionEvent.getY(0)).thenReturn(2f)
        Mockito.`when`(motionEvent.getX(1)).thenReturn(4f)
        Mockito.`when`(motionEvent.getY(1)).thenReturn(4f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify(currentTool, Mockito.never())?.handleMove(
            ArgumentMatchers.any(
                PointF::class.java
            ),
            ArgumentMatchers.any(
                Boolean::class.java
            )
        )
        Mockito.verify(callback, Mockito.atLeastOnce())
            ?.translatePerspective(ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat())
        Mockito.verify(callback)?.multiplyPerspectiveScale((2f - 4f) / (1f - 5f))
        Mockito.verify(callback)?.multiplyPerspectiveScale((1f - 5f) / (2f - 4f))
    }

    @Test
    fun testOnTouchUp() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked).thenReturn(MotionEvent.ACTION_UP)
        Mockito.`when`(motionEvent.x).thenReturn(3f)
        Mockito.`when`(motionEvent.y).thenReturn(5f)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify<Tool?>(currentTool).handleUp(pointFEquals(3f, 5f))
        Mockito.verify(currentTool)?.drawTime = ArgumentMatchers.anyLong()
    }

    @Test
    fun testOnTouchUpCompletesActiveGesture() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val motionEvent = Mockito.mock(MotionEvent::class.java)
        Mockito.`when`(motionEvent.actionMasked)
            .thenReturn(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)
        Mockito.`when`(motionEvent.x).thenReturn(3f)
        Mockito.`when`(motionEvent.y).thenReturn(5f)
        Mockito.`when`(motionEvent.pointerCount).thenReturn(2)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        drawingSurfaceListener!!.onTouch(drawingSurface, motionEvent)
        Mockito.verify(currentTool, Mockito.times(2))?.handleUp(
            ArgumentMatchers.nullable(PointF::class.java)
        )
        Mockito.verify(currentTool, Mockito.never())?.handleMove(
            ArgumentMatchers.any(
                PointF::class.java
            ),
            ArgumentMatchers.any(
                Boolean::class.java
            )
        )
    }

    @Test
    fun testTouchUpUsesTheActualEndpointAfterQuickMovement() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        triggerTouchDownEvent(initalPositionX, initalPositionY, drawingSurface)
        triggerMovementEventWithTimestamp(
            firstMovementPositionX,
            firstMovementPositionY,
            drawingSurface,
            firstMovementTimestamp
        )
        triggerMovementEventWithTimestamp(
            secondMovementPositionX,
            secondMovementPositionY,
            drawingSurface,
            secondMovementTimestamp
        )
        triggerTouchUpEvent(actionUpTimestamp, drawingSurface)
        Mockito.verify<Tool?>(currentTool)
            .handleUp(pointFEquals(actionUpMovementPositionX, actionUpMovementPositionY))
    }

    @Test
    fun testTouchCorrectionWithInvalidDelayBetweenMovements() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val tooLateTimestamp = secondMovementTimestamp + 15
        triggerTouchDownEvent(initalPositionX, initalPositionY, drawingSurface)
        triggerMovementEventWithTimestamp(
            firstMovementPositionX,
            firstMovementPositionY,
            drawingSurface,
            firstMovementTimestamp
        )
        triggerMovementEventWithTimestamp(
            secondMovementPositionX,
            secondMovementPositionY,
            drawingSurface,
            secondMovementTimestamp
        )
        triggerTouchUpEvent(tooLateTimestamp, drawingSurface)
        Mockito.verify<Tool?>(currentTool)
            .handleUp(ArgumentMatchers.nullable(PointF::class.java))
    }

    @Test
    fun testTouchCorrectionWithTooBigDelayBeforeUP() {
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        val muchLaterTimestamp: Long = 220
        triggerTouchDownEvent(initalPositionX, initalPositionY, drawingSurface)
        triggerMovementEventWithTimestamp(
            firstMovementPositionX,
            firstMovementPositionY,
            drawingSurface,
            firstMovementTimestamp
        )
        triggerMovementEventWithTimestamp(
            secondMovementPositionX,
            secondMovementPositionY,
            drawingSurface,
            secondMovementTimestamp
        )
        triggerTouchUpEvent(muchLaterTimestamp, drawingSurface)
        Mockito.verify<Tool?>(currentTool)
            .handleUp(ArgumentMatchers.nullable(PointF::class.java))
    }

    @Test
    fun testTouchCorrectionWithDistanceAboveJitterThreshold() {
        val farAwayPositionX = 20.0f
        val farAwayPositionY = 20.0f
        val drawingSurface = Mockito.mock(ZaintDrawingSurface::class.java)
        triggerTouchDownEvent(initalPositionX, initalPositionY, drawingSurface)
        triggerMovementEventWithTimestamp(
            farAwayPositionX,
            farAwayPositionY,
            drawingSurface,
            firstMovementTimestamp
        )
        triggerMovementEventWithTimestamp(
            secondMovementPositionX,
            secondMovementPositionY,
            drawingSurface,
            secondMovementTimestamp
        )
        triggerTouchUpEvent(actionUpTimestamp, drawingSurface)
        Mockito.verify<Tool?>(currentTool)
            .handleUp(ArgumentMatchers.nullable(PointF::class.java))
    }

    companion object {
        private const val DISPLAY_DENSITY = 1.5f
    }
}
