package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.tools.ZaintWarpOptionsPanel
import kotlin.math.hypot
import kotlin.math.min

/** Moves the pixels under one finger with a soft circular falloff. */
class WarpTool(
    private val options: ZaintWarpOptionsPanel,
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
    override val toolType: ZaintToolKind = ZaintToolKind.WARP
    override var drawTime: Long = 0L

    private var center = PointF(0f, 0f)
    private var radius = DEFAULT_RADIUS
    private val strength = DEFAULT_STRENGTH
    private var sourceBitmap: android.graphics.Bitmap? = null
    private val strokePoints = mutableListOf<PointF>()
    private var activeGesture = Gesture.NONE
    private var isCircleVisible = false
    private val previewHandler = Handler(Looper.getMainLooper())
    private val hideSliderPreview = Runnable {
        if (activeGesture == Gesture.NONE) isCircleVisible = false
        workspace.invalidate()
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }
    private val circleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    init {
        options.setCallback(object : ZaintWarpOptionsPanel.Callback {
            override fun onRadiusChanged(radius: Int) {
                this@WarpTool.radius = radius.toFloat()
                    .coerceIn(MIN_RADIUS, maxRadiusForCanvas())
                showCenteredAreaPreview()
            }
        })
        center.set(workspace.width / 2f, workspace.height / 2f)
        radius = min(DEFAULT_RADIUS, maxRadiusForCanvas())
        options.showRadius(radius.toInt())
        toolOptionsViewController.showDelayed()
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        previewHandler.removeCallbacks(hideSliderPreview)
        isCircleVisible = true
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        center.set(coordinate.x, coordinate.y)
        keepCircleIntersectingCanvas()
        startWarp(coordinate)
        activeGesture = Gesture.WARP
        workspace.invalidate()
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        if (activeGesture != Gesture.WARP) return false
        continueWarp(coordinate, shouldAnimate)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (activeGesture == Gesture.WARP) coordinate?.let {
            center.set(it.x, it.y)
            val previous = strokePoints.lastOrNull()
            if (previous == null || hypot(it.x - previous.x, it.y - previous.y) >= POINT_SPACING) {
                strokePoints += PointF(it.x, it.y)
            }
        }
        val bitmap = sourceBitmap.takeIf { activeGesture == Gesture.WARP }
        sourceBitmap = null
        if (bitmap != null && strokePoints.size > 1) {
            commandManager.addCommand(
                commandFactory.createWarpStrokeCommand(bitmap, strokePoints, radius, strength)
            )
        }
        strokePoints.clear()
        activeGesture = Gesture.NONE
        isCircleVisible = false
        previewHandler.removeCallbacks(hideSliderPreview)
        workspace.invalidate()
        return super.handleUp(coordinate)
    }

    override fun draw(canvas: Canvas) {
        if (!isCircleVisible) return
        circleShadowPaint.strokeWidth = 5f / workspace.scale.coerceAtLeast(MIN_SCALE)
        circlePaint.strokeWidth = 2f / workspace.scale.coerceAtLeast(MIN_SCALE)
        canvas.drawCircle(center.x, center.y, radius, circleShadowPaint)
        canvas.drawCircle(center.x, center.y, radius, circlePaint)
    }

    override fun resetInternalState() {
        sourceBitmap = null
        strokePoints.clear()
        activeGesture = Gesture.NONE
        isCircleVisible = false
        previewHandler.removeCallbacks(hideSliderPreview)
    }

    private fun startWarp(coordinate: PointF) {
        val bitmap = workspace.bitmapOfCurrentLayer ?: return
        sourceBitmap = bitmap
        strokePoints.clear()
        strokePoints += PointF(coordinate.x, coordinate.y)
    }

    private fun continueWarp(coordinate: PointF, shouldAnimate: Boolean) {
        if (sourceBitmap == null) return
        super.handleMove(coordinate, shouldAnimate)
        center.set(coordinate.x, coordinate.y)
        val previous = strokePoints.last()
        if (hypot(coordinate.x - previous.x, coordinate.y - previous.y) >= POINT_SPACING) {
            strokePoints += PointF(coordinate.x, coordinate.y)
        }
    }

    private fun maxRadiusForCanvas(): Float =
        AreaCircleBounds.maxRadiusForCanvas(workspace.width, workspace.height, MIN_RADIUS)

    private fun keepCircleIntersectingCanvas() {
        AreaCircleBounds.keepIntersectingCanvas(center, radius, workspace.width, workspace.height)
    }

    private fun showCenteredAreaPreview() {
        val visibleCenter = workspace.getCanvasPointFromSurfacePoint(
            PointF(workspace.surfaceWidth / 2f, workspace.surfaceHeight / 2f)
        )
        center.set(visibleCenter.x, visibleCenter.y)
        isCircleVisible = true
        previewHandler.removeCallbacks(hideSliderPreview)
        previewHandler.postDelayed(hideSliderPreview, SLIDER_PREVIEW_DURATION_MS)
        workspace.invalidate()
    }

    private enum class Gesture { NONE, WARP }

    companion object {
        private const val DEFAULT_RADIUS = 50f
        private const val MIN_RADIUS = 10f
        private const val DEFAULT_STRENGTH = 50
        private const val POINT_SPACING = 3f
        private const val MIN_SCALE = 0.1f
        private const val SLIDER_PREVIEW_DURATION_MS = 700L
    }
}
