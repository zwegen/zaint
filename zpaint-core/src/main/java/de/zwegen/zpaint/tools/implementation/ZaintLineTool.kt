package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.View
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.common.ZaintBrushOptionsListener
import de.zwegen.zpaint.tools.common.CommonBrushPreviewListener
import de.zwegen.zpaint.tools.options.ZaintBrushOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.viewholder.TopBarViewHolder

class ZaintLineTool(
    private val brushToolOptionsView: ZaintBrushOptions,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintShapeToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {
    override var toolType: ZaintToolKind = ZaintToolKind.LINE
    override fun handleUpAnimations(coordinate: PointF?) {
        super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    private val inputState = LineInputState()
    var lineFinalized: Boolean
        get() = inputState.isFinalized
        private set(value) { inputState.isFinalized = value }
    var endpointSet: Boolean
        get() = inputState.isEndSet
        private set(value) { inputState.isEndSet = value }
    var startpointSet: Boolean
        get() = inputState.isStartSet
        set(value) { inputState.isStartSet = value }
    var initialEventCoordinate: PointF?
        get() = inputState.initialCoordinate
        private set(value) { inputState.initialCoordinate = value }
    var startPointToDraw: PointF?
        get() = inputState.startPoint
        set(value) { inputState.startPoint = value }
    var endPointToDraw: PointF?
        get() = inputState.endPoint
        private set(value) { inputState.endPoint = value }
    var currentCoordinate: PointF?
        get() = inputState.currentCoordinate
        private set(value) { inputState.currentCoordinate = value }
    var toolSwitched: Boolean = false
    var lastSetStrokeWidth: Int = 0
    private val connectionState = LineConnectionState()
    private val styleUpdate = LineStyleUpdate()
    var connectedLines: Boolean
        get() = connectionState.isActive
        private set(value) {
            if (value) connectionState.begin() else connectionState.finish()
        }
    var undoRecentlyClicked: Boolean
        get() = connectionState.undoRecentlyClicked
        private set(value) {
            if (value) connectionState.markUndoRequested() else connectionState.clearUndoRequest()
        }
    var undoPreviousLineForConnectedLines = true
    var changeInitialCoordinateForHandleNormalLine = false

    companion object {
        private const val DEFAULT_LINE_STROKE_WIDTH = 12f

        var topBarViewHolder: TopBarViewHolder? = null
        private var savedLineColor = Color.BLACK
        private var savedLineStrokeWidth = DEFAULT_LINE_STROKE_WIDTH
    }

    init {
        restoreLineStyle()
        toolPaint.strokeCap = Paint.Cap.SQUARE
        brushToolOptionsView.setListener(ZaintBrushOptionsListener(this))
        brushToolOptionsView.setPreviewState(
            CommonBrushPreviewListener(
                toolPaint,
                toolType
            )
        )
        brushToolOptionsView.showPaint(toolPaint.paint)
        brushToolOptionsView.showStrokeCap(toolPaint.strokeCap)
        if (topBarViewHolder != null && topBarViewHolder?.plusButton?.visibility == View.VISIBLE) {
            topBarViewHolder?.hidePlusButton()
        }
    }

    private fun restoreLineStyle() {
        toolPaint.color = savedLineColor
        toolPaint.strokeWidth = savedLineStrokeWidth
    }

    override fun draw(canvas: Canvas) {
        LinePreview(initialEventCoordinate, currentCoordinate).draw(
            canvas,
            workspace.width,
            workspace.height,
            toolPaint.previewPaint
        )
    }

    fun handleStateBeforeUndo() {
        if (!lineFinalized && startpointSet && !connectedLines) {
            startpointSet = false
            startPointToDraw = null
        } else {
            if (!undoRecentlyClicked) {
                endpointSet = false
                endPointToDraw = null
            } else {
                undoPreviousLineForConnectedLines = true
                changeInitialCoordinateForHandleNormalLine = false
                lineFinalized = true
                resetInternalState()
            }
            connectionState.markUndoRequested()
        }
        val isPlusVisible = topBarViewHolder!!.plusButton.visibility == View.VISIBLE
        if (isPlusVisible && !connectedLines) {
            topBarViewHolder!!.plusButton.visibility = View.GONE
        }
    }

    override fun drawShape(canvas: Canvas) {
        // This should never be invoked
    }

    fun onClickOnPlus() {
        if (startpointSet && endpointSet) {
            val newStartCoordinate = inputState.beginConnectedSegment()
            previousEventCoordinate = newStartCoordinate?.let { PointF(it.x, it.y) }
            connectionState.begin()
            handleAddedLine(newStartCoordinate)
        }
    }

    override fun onClickOnButton() {
        if (topBarViewHolder != null && topBarViewHolder?.plusButton?.visibility == View.VISIBLE) {
            topBarViewHolder?.hidePlusButton()
        }
        undoRecentlyClicked = false
        if (startpointSet && endpointSet) {
            if (toolSwitched) {
                val finalPath = currentSegment().toPath()
                lineFinalized = true
                toolSwitched = false
                val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
                commandManager.addCommand(command)
            }
            lineFinalized = true
            resetInternalState()
        } else if (startpointSet && !endpointSet) {
            if (commandManager.isUndoAvailable) {
                commandManager.undoIgnoringColorChanges()
            }
            lineFinalized = true
            resetInternalState()
        } else {
            resetInternalState()
        }
    }

    private fun showToolOptions() {
        if (!toolOptionsViewController.isVisible) {
            if (brushToolOptionsView.bottomOptions().visibility == View.INVISIBLE) {
                toolOptionsViewController.slideDown(
                    brushToolOptionsView.topOptions(),
                    willHide = false,
                    showOptionsView = true
                )
            }

            if (brushToolOptionsView.bottomOptions().visibility == View.INVISIBLE) {
                toolOptionsViewController.slideUp(
                    brushToolOptionsView.bottomOptions(),
                    willHide = false,
                    showOptionsView = true
                )
            }
        }
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        super.handleDown(coordinate)
        inputState.beginDrag(coordinate)
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        super.handleMove(coordinate, shouldAnimate)
        changeInitialCoordinateForHandleNormalLine = true
        if (startpointSet) {
            previousEventCoordinate = inputState.continueFromStart()
            if (undoPreviousLineForConnectedLines && commandManager.isUndoAvailable && !undoRecentlyClicked) {
                undoRecentlyClicked = false
                commandManager.undoIgnoringColorChanges()
            }
            undoPreviousLineForConnectedLines = false
            undoRecentlyClicked = false
        }
        inputState.showPreview(coordinate)
        return true
    }

    fun handleStartPoint(xDistance: Float, yDistance: Float): Boolean {
        val startPoint = inputState.setStart(previousEventCoordinate, xDistance, yDistance)

        if (startPoint?.let { workspace.contains(it) } == true) {
            undoRecentlyClicked = false
            resetInternalState()
            return addPointCommand(startPoint)
        } else {
            lineFinalized = true
            resetInternalState()
        }
        return true
    }

    fun handleEndPoint(xDistance: Float, yDistance: Float, fromHandleLine: Boolean = false): Boolean {
        if (previousEventCoordinate?.let { workspace.contains(it) } == false) {
            return false
        }
        inputState.setEnd(previousEventCoordinate, xDistance, yDistance)
        val finalPath = currentSegment().toPath()
        val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)

        if (!fromHandleLine && !undoRecentlyClicked) {
            if (commandManager.isUndoAvailable && !undoRecentlyClicked) {
                commandManager.undoIgnoringColorChangesAndAddCommand(command)
            }
        } else {
            commandManager.addCommand(command)
        }
        undoRecentlyClicked = false
        resetInternalState()
        if (topBarViewHolder != null && topBarViewHolder?.plusButton?.visibility != View.VISIBLE) {
            topBarViewHolder?.showPlusButton()
        }
        return true
    }

    fun handleNormalLine(coordinate: PointF, xDistance: Float, yDistance: Float): Boolean {
        val bounds = RectF()
        if (startpointSet) {
            return handleEndPoint(xDistance, yDistance, true)
        }
        val finalPath = ZaintStrokePath().apply {
            moveTo(
                initialEventCoordinate?.x ?: return false,
                initialEventCoordinate?.y ?: return false
            )
            lineTo(coordinate.x, coordinate.y)
            computeBounds(bounds, true)
        }
        bounds.inset(-toolPaint.strokeWidth, -toolPaint.strokeWidth)

        previousEventCoordinate?.x = previousEventCoordinate?.x?.minus(xDistance)
        previousEventCoordinate?.y = previousEventCoordinate?.y?.minus(yDistance)
        startPointToDraw = initialEventCoordinate?.let { PointF(it.x, it.y) }
        endPointToDraw = previousEventCoordinate?.let { PointF(it.x, it.y) }

        endpointSet = true
        startpointSet = true
        undoRecentlyClicked = false

        if (topBarViewHolder != null && topBarViewHolder?.plusButton?.visibility != View.VISIBLE) {
            topBarViewHolder?.showPlusButton()
        }

        if (workspace.intersectsWith(bounds)) {
            val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
            commandManager.addCommand(command)
        }
        resetInternalState()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        showToolOptions()
        super.handleUp(coordinate)
        return handleAddedLine(coordinate)
    }

    fun handleAddedLine(coordinate: PointF?): Boolean {
        undoPreviousLineForConnectedLines = true
        if (changeInitialCoordinateForHandleNormalLine && initialEventCoordinate == null) {
            initialEventCoordinate = startPointToDraw?.let { PointF(it.x, it.y) }
        }
        if (initialEventCoordinate == null || previousEventCoordinate == null || coordinate == null) {
            changeInitialCoordinateForHandleNormalLine = false
            return false
        }
        val xDistance = initialEventCoordinate?.x?.minus(coordinate.x)
        val yDistance = initialEventCoordinate?.y?.minus(coordinate.y)
        if (xDistance != null && yDistance != null) {
            if (changeInitialCoordinateForHandleNormalLine) {
                changeInitialCoordinateForHandleNormalLine = false
                return handleNormalLine(coordinate, xDistance, yDistance)
            } else if (!startpointSet) {
                return handleStartPoint(xDistance, yDistance)
            } else {
                return handleEndPoint(xDistance, yDistance)
            }
        }
        changeInitialCoordinateForHandleNormalLine = false
        return true
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun resetInternalState() {
        inputState.clearTransientCoordinates()
        if (lineFinalized) {
            connectionState.finish()
            inputState.clearFinalizedLine()
        }
    }

    override fun changePaintColor(color: Int, invalidate: Boolean) {
        super.changePaintColor(color, invalidate)
        savedLineColor = toolPaint.color
        replayStyleUpdate()
        if (invalidate) brushToolOptionsView.refreshPreview()
    }

    fun undoChangePaintColor(color: Int, invalidate: Boolean) {
        handleStateBeforeUndo()
        super.changePaintColor(color, invalidate)
        savedLineColor = toolPaint.color
        if (invalidate) brushToolOptionsView.refreshPreview()
        if (connectedLines) {
            commandManager.undoInConnectedLinesMode()
        } else {
            commandManager.undo()
        }
    }

    fun redoLineTool() {
        undoRecentlyClicked = false
        if (connectedLines) {
            commandManager.redoInConnectedLinesMode()
        } else {
            commandManager.redo()
        }
    }

    fun undoColorChangedCommand(color: Int, invalidate: Boolean = true) {
        super.changePaintColor(color, invalidate)
        savedLineColor = toolPaint.color
        if (invalidate) brushToolOptionsView.refreshPreview()
    }

    override fun changePaintStrokeWidth(strokeWidth: Int) {
        super.changePaintStrokeWidth(strokeWidth)
        savedLineStrokeWidth = toolPaint.strokeWidth
        val noNewLine = lastSetStrokeWidth == strokeWidth
        replayStyleUpdate(styleChanged = !noNewLine)
        lastSetStrokeWidth = strokeWidth
        brushToolOptionsView.refreshPreview()
    }

    override fun changePaintStrokeCap(cap: Paint.Cap) {
        super.changePaintStrokeCap(cap)
        replayStyleUpdate()
        brushToolOptionsView.refreshPreview()
    }

    private fun addPointCommand(coordinate: PointF): Boolean {
        val command = commandFactory.createPointCommand(this.drawPaint, coordinate)
        commandManager.addCommand(command)
        return true
    }

    private fun currentSegment(): LineSegment = LineSegment(startPointToDraw, endPointToDraw)

    private fun replayStyleUpdate(styleChanged: Boolean = true) {
        when (
            styleUpdate.replayFor(
                hasStartPoint = startpointSet,
                hasEndPoint = endpointSet,
                isFinalized = lineFinalized,
                isUndoAvailable = commandManager.isUndoAvailable,
                undoRecentlyClicked = undoRecentlyClicked,
                styleChanged = styleChanged
            )
        ) {
            LineStyleReplay.SEGMENT -> commandManager.undoIgnoringColorChangesAndAddCommand(
                commandFactory.createPathCommand(toolPaint.paint, currentSegment().toPath())
            )
            LineStyleReplay.START_POINT -> startPointToDraw?.let {
                commandManager.undoIgnoringColorChangesAndAddCommand(
                    commandFactory.createPointCommand(drawPaint, it)
                )
            }
            LineStyleReplay.NONE -> Unit
        }
    }
}
