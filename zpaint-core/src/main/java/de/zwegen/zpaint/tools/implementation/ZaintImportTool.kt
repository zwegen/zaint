package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.os.Bundle
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.clipboard.ClipboardPlacement
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ImportToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val IMPORT_BITMAP_STATE = "zaint.import.bitmap"

/** Places an image selected by Zaint into the active layer or into a new layer. */
class ZaintImportTool(
    private val options: ImportToolOptionsView,
    contextCallback: ContextCallback,
    toolOptions: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintRectangleToolBase(
    contextCallback,
    toolOptions,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
), ZaintRectangleToolBase.ShapeSizeChangedListener {
    private val centerGuideSnap = CanvasCenterGuideSnap()
    private var showVerticalCenterGuide = false
    private var showHorizontalCenterGuide = false
    private val angleSnap = FloatingBoxAngleSnap()
    private var lockedSnapAngle: Float? = null
    private var showAngleSnapGuide = false
    private var rawBoxRotation = 0f

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.IMPORTPNG

    init {
        rotationEnabled = true
        setShapeSizeChangedListener(this)
        options.setShapeSizeInvisble()
    }

    override fun drawShape(canvas: Canvas) {
        if (drawingBitmap != null) super.drawShape(canvas)
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        resetCenterGuides()
        val handled = super.handleDown(coordinate)
        lockedSnapAngle = null
        showAngleSnapGuide = false
        rawBoxRotation = boxRotation
        return handled
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        val handled = super.handleMove(coordinate, shouldAnimate)
        if (isMovingFloatingBox()) snapToCanvasCenter(coordinate)
        return handled
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        resetCenterGuides()
        lockedSnapAngle = null
        showAngleSnapGuide = false
        return super.handleUp(coordinate)
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

    override fun rotationReferenceForDrag(): Float = rawBoxRotation

    override fun resolveBoxRotationAfterDrag(rawRotation: Float): Float {
        rawBoxRotation = rawRotation
        lockedSnapAngle = angleSnap.resolve(rawRotation, lockedSnapAngle)
        showAngleSnapGuide = lockedSnapAngle != null
        return lockedSnapAngle ?: rawRotation
    }

    override fun onSaveInstanceState(bundle: Bundle?) {
        super.onSaveInstanceState(bundle)
        bundle?.putParcelable(IMPORT_BITMAP_STATE, drawingBitmap)
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        drawingBitmap = bundle?.getParcelable(IMPORT_BITMAP_STATE)
    }

    override fun onClickOnButton() = placeImage()

    fun setBitmapFromSource(bitmap: Bitmap) {
        super.setBitmap(bitmap)
        val minSize = DEFAULT_BOX_RESIZE_MARGIN.toFloat()
        boxWidth = bitmap.width.toFloat().coerceIn(minSize, MAXIMUM_BORDER_RATIO * workspace.width)
        boxHeight = bitmap.height.toFloat().coerceIn(minSize, MAXIMUM_BORDER_RATIO * workspace.height)
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    override fun onShapeSizeChanged(shapeText: String) {
        options.setShapeSizeText(shapeText)
    }

    override fun onToggleVisibility(isVisible: Boolean) {
        showToolSpecificLayout()
    }

    override fun handleUpAnimations(coordinate: PointF?) {
        super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    private fun placeImage() {
        val bitmap = drawingBitmap ?: return
        highlightBox()
        val placement = ClipboardPlacement(
            Point(toolPosition.x.toInt(), toolPosition.y.toInt()),
            boxWidth,
            boxHeight,
            boxRotation
        )
        val insertion = commandFactory.createClipboardCommand(
            bitmap,
            toolPosition,
            boxWidth,
            boxHeight,
            boxRotation
        )
        val command = if (AutomaticLayerInsertion.shouldCreateNewLayer(workspace.layerModel.layerCount) {
                overlapsVisibleCurrentLayerPixels(
                    VisiblePixelOverlap.transformedBounds(toolPosition, boxWidth, boxHeight, boxRotation)
                ) { canvas -> placement.drawOn(canvas, bitmap) }
            }
        ) {
            ZaintCommandBatch().apply {
                addCommand(commandFactory.createAddEmptyLayerCommand())
                addCommand(insertion)
            }
        } else {
            insertion
        }
        commandManager.addCommand(command)
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
}
