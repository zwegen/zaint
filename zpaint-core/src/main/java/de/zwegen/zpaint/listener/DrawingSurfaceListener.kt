package de.zwegen.zpaint.listener

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.TwoFingerTransformTool
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.ZaintDrawingSurface
import kotlin.math.hypot

private const val DRAWER_EDGE_DP = 20f

/** Converts Android touch events into tool events or canvas pan/zoom gestures. */
open class DrawingSurfaceListener(
    private val callback: DrawingSurfaceListenerCallback,
    displayDensity: Float
) : View.OnTouchListener {
    private enum class Gesture { DRAW, PINCH, TOOL_TRANSFORM }

    private val edgeWidth = (DRAWER_EDGE_DP * displayDensity + .5f).toInt()
    private var gesture = Gesture.DRAW
    private var previousPointerDistance = 0f
    private var previousMidpoint = PointF()
    private var previousPanPoint = PointF()
    private var downTime = 0L
    private var contextOptionsWereVisible = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val surface = view as ZaintDrawingSurface
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !begin(event, view)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> Unit
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finish(event)
            MotionEvent.ACTION_POINTER_DOWN -> beginPinch(event)
            // A pinch stays a pinch until every finger has left the screen. In particular,
            // do not turn the remaining finger into a new tool interaction here.
            MotionEvent.ACTION_POINTER_UP -> Unit
        }
        surface.refreshDrawingSurface()
        return true
    }

    private fun begin(event: MotionEvent, view: View): Boolean {
        if (event.x < edgeWidth || view.width - event.x < edgeWidth) return false
        val toolOptionsController = callback.getZaintToolOptionsController()
        contextOptionsWereVisible = toolOptionsController.isVisible
        if (contextOptionsWereVisible) toolOptionsController.hide()
        gesture = Gesture.DRAW
        downTime = System.currentTimeMillis()
        previousPanPoint.set(event.x, event.y)
        callback.getCurrentTool()?.handleDown(toCanvasPoint(event.x, event.y))
        return true
    }

    private fun move(event: MotionEvent) {
        if (event.pointerCount >= 2) {
            if (gesture == Gesture.TOOL_TRANSFORM) {
                (callback.getCurrentTool() as? TwoFingerTransformTool)?.updateTwoFingerTransform(
                    toCanvasPoint(event.getX(0), event.getY(0)),
                    toCanvasPoint(event.getX(1), event.getY(1))
                )
                return
            }
            handlePinch(event)
            return
        }
        val tool = callback.getCurrentTool() ?: return
        // After a two-finger gesture, the remaining finger must be lifted before a tool
        // can receive another interaction. Otherwise a fill tool treats that final lift as
        // a tap and fills unexpectedly.
        if (gesture == Gesture.PINCH) return
        if (tool.handToolMode()) {
            callback.translatePerspective(event.x - previousPanPoint.x, event.y - previousPanPoint.y)
            previousPanPoint.set(event.x, event.y)
        } else {
            tool.handleMove(toCanvasPoint(event.x, event.y), false)
        }
    }

    private fun handlePinch(event: MotionEvent) {
        if (gesture != Gesture.PINCH) {
            beginPinch(event)
            return
        }
        val distance = pointerDistance(event)
        if (previousPointerDistance > 0f && distance > 0f) {
            callback.multiplyPerspectiveScale(distance / previousPointerDistance)
        }
        previousPointerDistance = distance
        val midpoint = midpoint(event)
        callback.translatePerspective(midpoint.x - previousMidpoint.x, midpoint.y - previousMidpoint.y)
        previousMidpoint = midpoint
    }

    private fun beginPinch(event: MotionEvent) {
        if (gesture == Gesture.PINCH || gesture == Gesture.TOOL_TRANSFORM) return
        val first = toCanvasPoint(event.getX(0), event.getY(0))
        val second = toCanvasPoint(event.getX(1), event.getY(1))
        val transformTool = callback.getCurrentTool() as? TwoFingerTransformTool
        if (transformTool?.beginTwoFingerTransform(first, second) == true) {
            gesture = Gesture.TOOL_TRANSFORM
            return
        }
        callback.getCurrentTool()?.resetInternalState(StateChange.MOVE_CANCELED)
        gesture = Gesture.PINCH
        previousPointerDistance = pointerDistance(event)
        previousMidpoint = midpoint(event)
    }

    private fun finish(event: MotionEvent) {
        val tool = callback.getCurrentTool()
        if (gesture == Gesture.DRAW) {
            tool?.drawTime = System.currentTimeMillis() - downTime
            tool?.handleUp(toCanvasPoint(event.x, event.y))
        } else if (gesture == Gesture.TOOL_TRANSFORM) {
            (tool as? TwoFingerTransformTool)?.endTwoFingerTransform()
        } else {
            tool?.resetInternalState(StateChange.MOVE_CANCELED)
        }
        gesture = Gesture.DRAW
        previousPointerDistance = 0f
        restoreContextOptionsIfNeeded()
    }

    private fun restoreContextOptionsIfNeeded() {
        if (!contextOptionsWereVisible) return
        callback.getZaintToolOptionsController().show()
        contextOptionsWereVisible = false
    }

    private fun toCanvasPoint(x: Float, y: Float): PointF {
        return point(x, y).also(callback::convertToCanvasFromSurface)
    }

    private fun point(x: Float, y: Float): PointF = PointF().apply {
        this.x = x
        this.y = y
    }

    private fun pointerDistance(event: MotionEvent): Float = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

    private fun midpoint(event: MotionEvent): PointF = point(
        (event.getX(0) + event.getX(1)) / 2f,
        (event.getY(0) + event.getY(1)) / 2f
    )

    interface DrawingSurfaceListenerCallback {
        fun getCurrentTool(): Tool?
        fun multiplyPerspectiveScale(factor: Float)
        fun translatePerspective(x: Float, y: Float)
        fun convertToCanvasFromSurface(surfacePoint: PointF)
        fun getZaintToolOptionsController(): ZaintToolOptionsController
    }
}
