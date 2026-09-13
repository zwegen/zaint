/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.PixelToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.hypot
import kotlin.math.min

class PixelTool(
    private val pixelToolOptionsView: PixelToolOptionsView,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline
) : ZaintToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {
    override val toolType: ZaintToolKind = ZaintToolKind.PIXEL
    override var drawTime: Long = 0L
    private var areaMode = lastAreaMode
    private var pixelSize = lastPixelSize
    private var center = PointF(0f, 0f)
    private var radius = DEFAULT_RADIUS
    private var activeGesture = Gesture.NONE
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = CIRCLE_STROKE_WIDTH
    }
    private val circleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = CIRCLE_SHADOW_STROKE_WIDTH
    }

    init {
        restoreCircle()
        pixelToolOptionsView.setPixelSizeRange(PIXEL_SIZE_MIN, PIXEL_SIZE_MAX, pixelSize)
        pixelToolOptionsView.setAreaMode(areaMode)
        pixelToolOptionsView.setCallback(object : PixelToolOptionsView.Callback {
            override fun setAreaMode(areaMode: Boolean) {
                this@PixelTool.areaMode = areaMode
                saveCurrentState()
                workspace.invalidate()
            }

            override fun setPixelSize(pixelSize: Int) {
                this@PixelTool.pixelSize = pixelSize
                saveCurrentState()
            }

            override fun applyPixelClicked() {
                applyPixel()
            }
        })
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun draw(canvas: Canvas) {
        if (!areaMode) {
            return
        }
        circlePaint.strokeWidth = scaledStrokeWidth(CIRCLE_STROKE_WIDTH)
        circleShadowPaint.strokeWidth = scaledStrokeWidth(CIRCLE_SHADOW_STROKE_WIDTH)
        canvas.drawCircle(center.x, center.y, radius, circleShadowPaint)
        canvas.drawCircle(center.x, center.y, radius, circlePaint)
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        if (!areaMode) {
            return true
        }
        activeGesture = if (isNearCircleEdge(coordinate)) {
            Gesture.RESIZE
        } else {
            Gesture.MOVE
        }
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        if (!areaMode) {
            return true
        }
        when (activeGesture) {
            Gesture.RESIZE -> {
                radius = distanceToCenter(coordinate).coerceIn(MIN_RADIUS, maxRadiusForCanvas())
                keepCircleIntersectingCanvas()
                saveCurrentState()
            }
            Gesture.MOVE -> {
                val previous = previousEventCoordinate ?: coordinate
                center.offset(coordinate.x - previous.x, coordinate.y - previous.y)
                keepCircleIntersectingCanvas()
                saveCurrentState()
            }
            Gesture.NONE -> Unit
        }
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        activeGesture = Gesture.NONE
        saveCurrentState()
        return super.handleUp(coordinate)
    }

    override fun resetInternalState(stateChange: Tool.StateChange) {
        if (stateChange == Tool.StateChange.NEW_IMAGE_LOADED) {
            clearSavedState()
            resetCircle()
            saveCurrentState()
        }
    }

    fun applyPixel() {
        if (areaMode) {
            keepCircleIntersectingCanvas()
        }
        commandManager.addCommand(
            commandFactory.createPixelateCommand(
                pixelSize,
                areaMode,
                center.x,
                center.y,
                radius
            )
        )
        saveCurrentState()
        workspace.invalidate()
    }

    private fun restoreCircle() {
        val savedX = lastCenterX
        val savedY = lastCenterY
        if (savedX == null || savedY == null) {
            resetCircle()
            saveCurrentState()
            return
        }
        center = PointF(savedX, savedY)
        radius = lastRadius
        radius = radius.coerceAtLeast(MIN_RADIUS).coerceAtMost(maxRadiusForCanvas())
        keepCircleIntersectingCanvas()
    }

    private fun resetCircle() {
        center = PointF(workspace.width / 2f, workspace.height / 2f)
        radius = min(workspace.width, workspace.height) * DEFAULT_RADIUS_FACTOR
        radius = radius.coerceAtLeast(MIN_RADIUS).coerceAtMost(maxRadiusForCanvas())
    }

    private fun keepCircleIntersectingCanvas() {
        AreaCircleBounds.keepIntersectingCanvas(center, radius, workspace.width, workspace.height)
    }

    private fun maxRadiusForCanvas(): Float =
        AreaCircleBounds.maxRadiusForCanvas(workspace.width, workspace.height, MIN_RADIUS)

    private fun isNearCircleEdge(coordinate: PointF): Boolean {
        val distance = distanceToCenter(coordinate)
        val innerResizeBand = min(radius * EDGE_INNER_RADIUS_FACTOR, touchToleranceForZoom())
        return distance >= radius - innerResizeBand && distance <= radius + touchToleranceForZoom()
    }

    private fun distanceToCenter(coordinate: PointF): Float =
        hypot((coordinate.x - center.x).toDouble(), (coordinate.y - center.y).toDouble()).toFloat()

    private fun scaledStrokeWidth(defaultStrokeWidth: Float): Float =
        defaultStrokeWidth / workspace.scale.coerceAtLeast(MIN_SCALE_FOR_DRAWING)

    private fun touchToleranceForZoom(): Float =
        EDGE_TOUCH_TOLERANCE / workspace.scale.coerceAtLeast(MIN_SCALE_FOR_DRAWING)

    private fun saveCurrentState() {
        lastAreaMode = areaMode
        lastPixelSize = pixelSize
        lastCenterX = center.x
        lastCenterY = center.y
        lastRadius = radius
    }

    private enum class Gesture {
        NONE, MOVE, RESIZE
    }

    companion object {
        private const val PIXEL_SIZE_MIN = 1
        private const val PIXEL_SIZE_MAX = 50
        private const val DEFAULT_PIXEL_SIZE = 12
        private const val MIN_RADIUS = 8f
        private const val DEFAULT_RADIUS = 80f
        private const val DEFAULT_RADIUS_FACTOR = 0.25f
        private const val EDGE_TOUCH_TOLERANCE = 28f
        private const val EDGE_INNER_RADIUS_FACTOR = 0.35f
        private const val CIRCLE_STROKE_WIDTH = 3f
        private const val CIRCLE_SHADOW_STROKE_WIDTH = 7f
        private const val MIN_SCALE_FOR_DRAWING = 0.1f
        private var lastAreaMode = false
        private var lastPixelSize = DEFAULT_PIXEL_SIZE
        private var lastCenterX: Float? = null
        private var lastCenterY: Float? = null
        private var lastRadius = DEFAULT_RADIUS

        private fun clearSavedState() {
            lastAreaMode = false
            lastPixelSize = DEFAULT_PIXEL_SIZE
            lastCenterX = null
            lastCenterY = null
            lastRadius = DEFAULT_RADIUS
        }
    }
}
