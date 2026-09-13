package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.os.Bundle
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.clipboard.ClipboardPlacement
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.IconResult
import de.zwegen.zpaint.tools.options.IconToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val ICON_SEARCH_PAGE_SIZE = 48
private const val ICON_PREVIEW_SIZE = 96
private const val ICON_INSERT_SIZE = 768
private const val BUNDLE_ICON_DRAWING_BITMAP = "BUNDLE_ICON_DRAWING_BITMAP"

class IconTool(
    private val iconToolOptionsView: IconToolOptionsView,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintRectangleToolBase(
    contextCallback, toolOptionsViewController, toolPaint, workspace, idlingResource, commandManager
), ZaintRectangleToolBase.ShapeSizeChangedListener {
    private val iconifyClient = IconifyClient()
    private val svgBitmapRenderer = SvgBitmapRenderer()
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val loadedResults = mutableListOf<IconResult>()
    private val previewCache = mutableMapOf<String, Bitmap>()
    private var currentQuery = ""
    private var canLoadMoreIcons = false
    private val centerGuideSnap = CanvasCenterGuideSnap()
    private var showVerticalCenterGuide = false
    private var showHorizontalCenterGuide = false
    private val angleSnap = FloatingBoxAngleSnap()
    private var lockedSnapAngle: Float? = null
    private var showAngleSnapGuide = false
    private var rawBoxRotation = 0f

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.ICON

    override fun handleUpAnimations(coordinate: PointF?) {
        super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    init {
        rotationEnabled = true
        setShapeSizeChangedListener(this)
        iconToolOptionsView.setCallback(
            object : IconToolOptionsView.Callback {
                override fun searchIcons(query: String) {
                    this@IconTool.searchIcons(query)
                }

                override fun loadMoreIcons() {
                    this@IconTool.loadMoreIcons()
                }

                override fun selectIcon(icon: IconResult) {
                    this@IconTool.selectIcon(icon.id)
                }
            }
        )
        toolOptionsViewController.showDelayed()
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    override fun drawShape(canvas: Canvas) {
        if (drawingBitmap != null) {
            super.drawShape(canvas)
        }
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
        bundle?.putParcelable(BUNDLE_ICON_DRAWING_BITMAP, drawingBitmap)
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        drawingBitmap = bundle?.getParcelable(BUNDLE_ICON_DRAWING_BITMAP)
    }

    override fun onClickOnButton() = insertIntoLayer()

    private fun insertIntoLayer() {
        val bitmap = drawingBitmap
        if (bitmap == null) {
            contextCallback.showNotification(R.string.icon_tool_select_hint)
            return
        }
        if (boxIntersectsWorkspace()) {
            highlightBox()
            val command = commandFactory.createClipboardCommand(
                bitmap,
                toolPosition,
                boxWidth,
                boxHeight,
                boxRotation
            )
            commandManager.addCommand(
                if (AutomaticLayerInsertion.shouldCreateNewLayer(workspace.layerModel.layerCount) {
                        overlapsVisibleCurrentLayerPixels(
                            VisiblePixelOverlap.transformedBounds(toolPosition, boxWidth, boxHeight, boxRotation)
                        ) { canvas ->
                            ClipboardPlacement(
                                Point(toolPosition.x.toInt(), toolPosition.y.toInt()),
                                boxWidth,
                                boxHeight,
                                boxRotation
                            ).drawOn(canvas, bitmap)
                        }
                    }
                ) {
                    ZaintCommandBatch().apply {
                        addCommand(commandFactory.createAddEmptyLayerCommand())
                        addCommand(command)
                    }
                } else {
                    command
                }
            )
        }
    }

    private fun searchIcons(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            iconToolOptionsView.showMessage(R.string.icon_tool_empty_query)
            return
        }
        currentQuery = trimmedQuery
        loadedResults.clear()
        canLoadMoreIcons = false
        iconToolOptionsView.showResults(emptyList(), false)
        loadIcons(trimmedQuery, ICON_SEARCH_PAGE_SIZE)
    }

    private fun loadMoreIcons() {
        if (currentQuery.isNotBlank() && canLoadMoreIcons) {
            loadIcons(currentQuery, loadedResults.size + ICON_SEARCH_PAGE_SIZE)
        }
    }

    private fun loadIcons(query: String, displayLimit: Int) {
        iconToolOptionsView.setLoading(true)
        mainScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val iconIds = iconifyClient.search(query, displayLimit + 1)
                    canLoadMoreIcons = iconIds.size > displayLimit
                    iconIds
                        .take(displayLimit)
                        .filterNot { loadedResult -> loadedResults.any { it.id == loadedResult } }
                        .mapNotNull { iconId -> loadPreview(iconId) }
                }
            }.onSuccess { results ->
                loadedResults.addAll(results)
                iconToolOptionsView.setLoading(false)
                iconToolOptionsView.showResults(loadedResults, canLoadMoreIcons)
                val message = if (loadedResults.isEmpty()) {
                    R.string.icon_tool_no_results
                } else {
                    R.string.icon_tool_results_loaded
                }
                iconToolOptionsView.showMessage(message)
            }.onFailure {
                iconToolOptionsView.setLoading(false)
                iconToolOptionsView.showResults(loadedResults, canLoadMoreIcons)
                iconToolOptionsView.showMessage(R.string.icon_tool_error)
            }
        }
    }

    private fun loadPreview(iconId: String): IconResult? =
        runCatching {
            val preview = previewCache.getOrPut(iconId) {
                val svg = iconifyClient.loadSvg(iconId)
                svgBitmapRenderer.render(svg, ICON_PREVIEW_SIZE)
            }
            IconResult(iconId, preview)
        }.getOrNull()

    private fun selectIcon(iconId: String) {
        iconToolOptionsView.setLoading(true)
        mainScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val svg = iconifyClient.loadSvg(iconId)
                    svgBitmapRenderer.render(svg, ICON_INSERT_SIZE)
                }
            }.onSuccess { bitmap ->
                iconToolOptionsView.setLoading(false)
                setBitmapFromSource(bitmap)
                toolOptionsViewController.hide()
                iconToolOptionsView.showMessage(iconId)
            }.onFailure {
                iconToolOptionsView.setLoading(false)
                iconToolOptionsView.showMessage(R.string.icon_tool_error)
            }
        }
    }

    private fun setBitmapFromSource(bitmap: Bitmap) {
        setBitmap(bitmap)
        val maximumBorderRatioWidth = MAXIMUM_BORDER_RATIO * workspace.width
        val maximumBorderRatioHeight = MAXIMUM_BORDER_RATIO * workspace.height
        val minimumSize = DEFAULT_BOX_RESIZE_MARGIN.toFloat()
        boxWidth = max(minimumSize, min(maximumBorderRatioWidth, bitmap.width.toFloat()))
        boxHeight = max(minimumSize, min(maximumBorderRatioHeight, bitmap.height.toFloat()))
        createAndSetShapeSizeText(boxWidth, boxHeight)
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

    override fun onShapeSizeChanged(shapeText: String) {
        iconToolOptionsView.setShapeSizeText(shapeText)
    }

    override fun onToggleVisibility(isVisible: Boolean) {
        iconToolOptionsView.toggleShapeSizeVisibility(isVisible)
    }

    fun changeIconToolLayoutVisibility(willHide: Boolean, disabled: Boolean = false) {
        changeToolLayoutVisibility(iconToolOptionsView.getIconToolOptionsLayout(), willHide, disabled)
    }
}
