package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintSelectionClear
import de.zwegen.zpaint.command.implementation.ZaintSelectionShape
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.SelectionShape
import de.zwegen.zpaint.tools.options.SelectionToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** Zaint's selection interaction, including rectangle, circle and free-path selections. */
class ZaintSelectionTool(
    options: SelectionToolOptionsView,
    contextCallback: ContextCallback,
    toolOptions: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    drawTime: Long
) : ZaintClipboardTool(
    options,
    contextCallback,
    toolOptions,
    toolPaint,
    workspace,
    idlingResource,
    commandManager,
    drawTime
) {
    private var mode = SelectionShape.RECTANGLE
    private val freePath = ZaintStrokePath()
    private val freeBounds = RectF()
    private var freeStart: PointF? = null
    private var tracingFreePath = false
    private val angleSnap = FloatingBoxAngleSnap()
    private var lockedSnapAngle: Float? = null
    private var showAngleSnapGuide = false
    private var rawBoxRotation = 0f
    private val pathPreview = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    init {
        options.setShapeChangedListener(::selectShape)
    }

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.SELECTION

    override fun onClickOnNewLayerButton() {
        if (!readyForPaste || drawingBitmap == null) {
            contextCallback.showNotification(R.string.clipboard_tool_copy_hint)
        } else if (boxIntersectsWorkspace()) {
            pasteBoxContent(onNewLayer = true)
            highlightBox()
        }
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        if (!usesFreePath() || readyForPaste) {
            val handled = super.handleDown(coordinate)
            lockedSnapAngle = null
            showAngleSnapGuide = false
            rawBoxRotation = boxRotation
            return handled
        }
        coordinate ?: return false

        readyForPaste = false
        drawingBitmap = null
        tracingFreePath = true
        freeStart = PointF(coordinate.x, coordinate.y)
        freePath.rewind()
        freePath.moveTo(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        if (!usesFreePath() || readyForPaste || !tracingFreePath) {
            return super.handleMove(coordinate, shouldAnimate)
        }
        coordinate ?: return false

        freePath.lineTo(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (!usesFreePath() || readyForPaste || !tracingFreePath) {
            lockedSnapAngle = null
            showAngleSnapGuide = false
            return super.handleUp(coordinate)
        }

        coordinate?.let { freePath.lineTo(it.x, it.y) }
        freeStart?.let { freePath.lineTo(it.x, it.y) }
        tracingFreePath = false
        workspace.invalidate()
        return true
    }

    override fun draw(canvas: Canvas) {
        if (!usesFreePath() || readyForPaste) {
            super.draw(canvas)
            return
        }
        pathPreview.strokeWidth = toolStrokeWidth * 2f
        canvas.drawPath(freePath, pathPreview)
    }

    override fun drawToolSpecifics(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        drawAngleSnapGuide(canvas)
        super.drawToolSpecifics(canvas, boxWidth, boxHeight)
    }

    override fun rotationReferenceForDrag(): Float = rawBoxRotation

    override fun resolveBoxRotationAfterDrag(rawRotation: Float): Float {
        rawBoxRotation = rawRotation
        lockedSnapAngle = angleSnap.resolve(rawRotation, lockedSnapAngle)
        showAngleSnapGuide = lockedSnapAngle != null
        return lockedSnapAngle ?: rawRotation
    }

    override fun copyBoxContent() {
        if (!usesFreePath()) {
            super.copyBoxContent()
        } else if (hasFreePath()) {
            copyFreePath()
        } else {
            showFreePathHint()
        }
    }

    override fun cutBoxContent() {
        if (!usesFreePath()) {
            super.cutBoxContent()
        } else if (hasFreePath()) {
            copyFreePath()
            removeFreePath(clearPath = false)
        } else {
            showFreePathHint()
        }
    }

    override fun deleteSelectionContent() {
        if (!usesFreePath()) {
            super.deleteSelectionContent()
        } else if (hasFreePath()) {
            removeFreePath(clearPath = true)
        } else {
            showFreePathHint()
        }
    }

    private fun selectShape(shape: SelectionShape) {
        mode = shape
        floatingBoxShape = when (shape) {
            SelectionShape.RECTANGLE -> FloatingBoxShape.RECTANGLE
            SelectionShape.CIRCLE -> FloatingBoxShape.OVAL
            SelectionShape.FREE -> {
                clearFreePath()
                FloatingBoxShape.RECTANGLE
            }
        }
        workspace.invalidate()
    }

    private fun copyFreePath() {
        val layer = workspace.bitmapOfCurrentLayer ?: return
        val bounds = freePathBounds()
        val width = max(1, ceil(bounds.width()).toInt())
        val height = max(1, ceil(bounds.height()).toInt())
        drawingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
            val localPath = Path(freePath).apply { offset(-bounds.left, -bounds.top) }
            Canvas(target).apply {
                clipPath(localPath)
                drawBitmap(layer, -bounds.left, -bounds.top, null)
            }
        }
        boxWidth = width.toFloat()
        boxHeight = height.toFloat()
        toolPosition.set(bounds.left + boxWidth / 2f, bounds.top + boxHeight / 2f)
        boxRotation = 0f
        floatingBoxShape = FloatingBoxShape.RECTANGLE
        readyForPaste = true
        workspace.invalidate()
    }

    private fun drawAngleSnapGuide(canvas: Canvas) {
        val snappedAngle = lockedSnapAngle ?: return
        if (!showAngleSnapGuide) return
        val horizontal = snappedAngle == 0f || abs(snappedAngle) == 180f
        val start = if (horizontal) {
            workspacePointToBoxCanvasPoint(0f, toolPosition.y)
        } else {
            workspacePointToBoxCanvasPoint(toolPosition.x, 0f)
        }
        val stop = if (horizontal) {
            workspacePointToBoxCanvasPoint(workspace.width.toFloat(), toolPosition.y)
        } else {
            workspacePointToBoxCanvasPoint(toolPosition.x, workspace.height.toFloat())
        }
        CanvasCenterGuideRenderer.drawGuideLine(canvas, start.x, start.y, stop.x, stop.y, workspace.scale, linePaint)
    }

    private fun workspacePointToBoxCanvasPoint(x: Float, y: Float): PointF {
        val radians = Math.toRadians((-boxRotation).toDouble())
        val deltaX = x - toolPosition.x
        val deltaY = y - toolPosition.y
        return PointF(
            (deltaX * cos(radians) - deltaY * sin(radians)).toFloat(),
            (deltaX * sin(radians) + deltaY * cos(radians)).toFloat()
        )
    }

    private fun removeFreePath(clearPath: Boolean) {
        commandManager.addCommand(
            ZaintSelectionClear(
                Point(0, 0),
                0f,
                0f,
                0f,
                ZaintSelectionShape.PATH,
                ZaintStrokePath(freePath)
            )
        )
        if (clearPath) clearFreePath()
        workspace.invalidate()
    }

    private fun usesFreePath() = mode == SelectionShape.FREE

    private fun hasFreePath(): Boolean {
        freePath.computeBounds(freeBounds, true)
        return !freeBounds.isEmpty
    }

    private fun freePathBounds(): RectF {
        freePath.computeBounds(freeBounds, true)
        return RectF(
            floor(freeBounds.left),
            floor(freeBounds.top),
            ceil(freeBounds.right),
            ceil(freeBounds.bottom)
        )
    }

    private fun clearFreePath() {
        tracingFreePath = false
        freeStart = null
        freePath.rewind()
        readyForPaste = false
        boxRotation = 0f
    }

    private fun showFreePathHint() {
        contextCallback.showNotification(R.string.selection_tool_free_hint)
    }
}
