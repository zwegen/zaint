package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.ViewConfiguration
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.FloatingSelectionLayerCommand
import de.zwegen.zpaint.command.implementation.MotifSelectionLayerCommand
import de.zwegen.zpaint.command.implementation.PathSelectionLayerCommand
import de.zwegen.zpaint.command.implementation.SelectionLayerCommand
import de.zwegen.zpaint.command.implementation.ZaintSelectionClear
import de.zwegen.zpaint.command.implementation.ZaintSelectionShape
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.motif.MotifSelection
import de.zwegen.zpaint.motif.MotifSelectionTarget
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.SelectionShape
import de.zwegen.zpaint.tools.options.SelectionToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** Zaint's selection interaction, including geometric, free-path and motif selections. */
class ZaintSelectionTool(
    private val options: SelectionToolOptionsView,
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
    private val motif by lazy { MotifSelection(contextCallback.context, ::motifChanged) }
    private var target = MotifSelectionTarget.OBJECTS
    private var confirmWhenReady = false
    private var motifDown: PointF? = null
    private val tapSlop by lazy { ViewConfiguration.get(contextCallback.context).scaledTouchSlop }
    private var motifSource: Bitmap? = null
    private var sourceGeneration = 0
    private var sourceRevision = 0L
    private val motifPreviewPaint = Paint().apply {
        colorFilter = PorterDuffColorFilter(Color.rgb(45, 180, 255), PorterDuff.Mode.SRC_IN)
        alpha = 112
    }
    private var clipboardSource: WeakReference<Bitmap>? = null
    private var clipboardShape: SelectionShape? = null
    private var mode = SelectionShape.MOTIF
    private val freePath = ZaintStrokePath()
    private val freeBounds = RectF()
    private var freeStart: PointF? = null
    private var tracingFreePath = false
    private val angleSnap = FloatingBoxAngleSnap()
    private val centerGuideSnap = CanvasCenterGuideSnap()
    private var showVerticalCenterGuide = false
    private var showHorizontalCenterGuide = false
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
        options.setMotifTarget(target)
        toolOptionsViewController.hideLayerPlus()
        options.setShapeChangedListener(::selectShape)
    }

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.SELECTION

    override fun onClickOnButton() {
        if (commandManager.isBusy) return
        if (mode == SelectionShape.MOTIF) {
            applyMotif()
            return
        }
        val source = workspace.layerModel.currentLayer?.bitmap ?: return
        val floating = drawingBitmap
        val command = if (readyForPaste && floating != null && clipboardShape == mode) {
            if (clipboardSource?.get() !== source) return
            FloatingSelectionLayerCommand(
                commandFactory.createClipboardCommand(floating, toolPosition, boxWidth, boxHeight, boxRotation)
            )
        } else {
            val path = selectionPath() ?: return
            PathSelectionLayerCommand(path)
        }
        submitCutout(command, source)
    }

    private fun selectionPath(): Path? {
        if (usesFreePath()) return if (hasFreePath() && !tracingFreePath) Path(freePath) else null
        if (boxWidth <= 0f || boxHeight <= 0f) return null
        val bounds = RectF(-boxWidth / 2, -boxHeight / 2, boxWidth / 2, boxHeight / 2)
        return Path().apply {
            if (mode == SelectionShape.CIRCLE) addOval(bounds, Path.Direction.CW)
            else addRect(bounds, Path.Direction.CW)
            transform(Matrix().apply { setRotate(boxRotation) })
            offset(toolPosition.x, toolPosition.y)
        }
    }

    private fun submitCutout(command: SelectionLayerCommand, source: Bitmap) {
        command.expectedSource = source
        command.onFirstApplied = options::showCutoutConfirmation
        commandManager.addCommand(command)
    }

    override fun onClickOnNewLayerButton() {
        if (mode == SelectionShape.MOTIF) return
        if (!readyForPaste || drawingBitmap == null) {
            contextCallback.showNotification(R.string.clipboard_tool_copy_hint)
        } else if (boxIntersectsWorkspace()) {
            pasteBoxContent(onNewLayer = true)
            highlightBox()
        }
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        resetCenterGuides()
        if (mode == SelectionShape.MOTIF) {
            motifDown = coordinate?.let { PointF(it.x, it.y) }
            return coordinate != null
        }
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
        if (mode == SelectionShape.MOTIF) {
            if (!withinTapSlop(coordinate)) motifDown = null
            return true
        }
        if (!usesFreePath() || readyForPaste || !tracingFreePath) {
            val handled = super.handleMove(coordinate, shouldAnimate)
            if (handled && readyForPaste && isMovingFloatingBox()) {
                snapToCanvasCenter(coordinate)
            }
            return handled
        }
        coordinate ?: return false

        freePath.lineTo(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (mode == SelectionShape.MOTIF) {
            val tapped = withinTapSlop(coordinate)
            motifDown = null
            if (tapped && coordinate != null && workspace.contains(coordinate)) tapMotif(coordinate)
            return true
        }
        resetCenterGuides()
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
        if (mode == SelectionShape.MOTIF) {
            val source = motifSource ?: return
            val mask = motif.mask
            if (mask != null && sourceIsCurrent()) {
                // Isolate masking from the artwork underneath, then tint just the cutout.
                val layer = canvas.saveLayer(0f, 0f, source.width.toFloat(), source.height.toFloat(), motifPreviewPaint)
                target.drawCutout(canvas, source, mask)
                canvas.restoreToCount(layer)
            }
            return
        }
        if (!usesFreePath() || readyForPaste) {
            super.draw(canvas)
            return
        }
        pathPreview.strokeWidth = toolStrokeWidth * 2f
        canvas.drawPath(freePath, pathPreview)
    }

    override fun drawToolSpecifics(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        CanvasCenterGuideRenderer.draw(
            canvas,
            workspace.width.toFloat(),
            workspace.height.toFloat(),
            toolPosition.x,
            toolPosition.y,
            boxRotation,
            workspace.scale,
            linePaint,
            showVerticalCenterGuide,
            showHorizontalCenterGuide
        )
        drawAngleSnapGuide(canvas)
        super.drawToolSpecifics(canvas, boxWidth, boxHeight)
    }

    private fun snapToCanvasCenter(coordinate: PointF?) {
        val result = centerGuideSnap.resolve(
            toolPosition.x,
            toolPosition.y,
            coordinate?.x,
            coordinate?.y,
            workspace.width.toFloat(),
            workspace.height.toFloat()
        )
        toolPosition.set(result.x, result.y)
        showVerticalCenterGuide = result.showVerticalGuide
        showHorizontalCenterGuide = result.showHorizontalGuide
        workspace.invalidate()
    }

    private fun resetCenterGuides() {
        centerGuideSnap.reset()
        showVerticalCenterGuide = false
        showHorizontalCenterGuide = false
    }

    override fun rotationReferenceForDrag(): Float = rawBoxRotation

    override fun resolveBoxRotationAfterDrag(rawRotation: Float): Float {
        rawBoxRotation = rawRotation
        lockedSnapAngle = angleSnap.resolve(rawRotation, lockedSnapAngle)
        showAngleSnapGuide = lockedSnapAngle != null
        return lockedSnapAngle ?: rawRotation
    }

    override fun copyBoxContent() {
        if (mode == SelectionShape.MOTIF) return
        if (!usesFreePath()) {
            super.copyBoxContent()
            rememberClipboardSource()
        } else if (hasFreePath()) {
            copyFreePath()
            rememberClipboardSource()
        } else {
            showFreePathHint()
        }
    }

    private fun rememberClipboardSource() {
        clipboardSource = WeakReference(workspace.layerModel.currentLayer?.bitmap)
        clipboardShape = mode
    }

    override fun cutBoxContent() {
        if (mode == SelectionShape.MOTIF) return
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
        if (mode == SelectionShape.MOTIF) {
            clearMotif()
            return
        }
        if (!usesFreePath()) {
            super.deleteSelectionContent()
        } else if (hasFreePath()) {
            removeFreePath(clearPath = true)
        } else {
            showFreePathHint()
        }
    }

    private fun selectShape(shape: SelectionShape) {
        if (mode == SelectionShape.MOTIF) {
            if (shape == mode) {
                confirmWhenReady = false
                target = if (target == MotifSelectionTarget.OBJECTS) {
                    MotifSelectionTarget.BACKGROUND
                } else {
                    MotifSelectionTarget.OBJECTS
                }
                options.setMotifTarget(target)
                contextCallback.showNotification(target.label)
                workspace.invalidate()
                return
            }
            clearMotif()
            toolOptionsViewController.showLayerPlus()
        }
        mode = shape
        floatingBoxShape = when (shape) {
            SelectionShape.RECTANGLE -> FloatingBoxShape.RECTANGLE
            SelectionShape.CIRCLE -> FloatingBoxShape.OVAL
            SelectionShape.MOTIF -> {
                target = MotifSelectionTarget.OBJECTS
                options.setMotifTarget(target)
                toolOptionsViewController.hideLayerPlus()
                clearFreePath()
                drawingBitmap = null
                contextCallback.showNotification(R.string.selection_motif_hint)
                FloatingBoxShape.RECTANGLE
            }
            SelectionShape.FREE -> {
                clearFreePath()
                FloatingBoxShape.RECTANGLE
            }
        }
        workspace.invalidate()
    }

    fun cancelMotifTap(): Boolean {
        if (mode != SelectionShape.MOTIF) return false
        motifDown = null
        return true
    }

    private fun withinTapSlop(point: PointF?): Boolean {
        val start = motifDown ?: return false
        point ?: return false
        return PointF.length(point.x - start.x, point.y - start.y) * workspace.scale <= tapSlop
    }

    private fun tapMotif(point: PointF) {
        if (!sourceIsCurrent()) clearMotif()
        if (commandManager.isBusy || confirmWhenReady) return
        val source = workspace.layerModel.currentLayer?.bitmap ?: return
        motifSource = source
        sourceGeneration = source.generationId
        sourceRevision = commandManager.revision
        motif.tap(source, point.x.toInt(), point.y.toInt())
    }

    private fun sourceIsCurrent(): Boolean {
        val source = motifSource ?: return false
        return !commandManager.isBusy && commandManager.revision == sourceRevision &&
            source === workspace.layerModel.currentLayer?.bitmap &&
            !source.isRecycled && source.generationId == sourceGeneration
    }

    private fun motifChanged() {
        if (motifSource != null && !sourceIsCurrent()) {
            clearMotif()
            return
        }
        if (motif.failed) {
            confirmWhenReady = false
            contextCallback.showNotification(R.string.selection_motif_failed)
        } else if (confirmWhenReady && !motif.busy) {
            confirmWhenReady = false
            applyMotif()
        }
        workspace.invalidate()
    }

    private fun applyMotif() {
        if (!sourceIsCurrent()) {
            clearMotif()
            contextCallback.showNotification(R.string.selection_motif_hint)
            return
        }
        val mask = motif.mask
        when {
            motif.busy -> {
                confirmWhenReady = true
                contextCallback.showNotification(R.string.selection_motif_busy)
            }
            mask == null -> contextCallback.showNotification(R.string.selection_motif_hint)
            else -> {
                val command = MotifSelectionLayerCommand(mask, target = target)
                val source = motifSource ?: return
                clearMotif()
                submitCutout(command, source)
            }
        }
    }

    private fun clearMotif() {
        motifDown = null
        confirmWhenReady = false
        if (motifSource == null) return
        motifSource = null
        motif.clear()
    }

    override fun resetInternalState(stateChange: StateChange) {
        if (stateChange == StateChange.MOVE_CANCELED) motifDown = null else clearMotif()
        super.resetInternalState(stateChange)
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
