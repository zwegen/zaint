/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewConfiguration
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.MotifFilterCommand
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.motif.MotifSelection
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.FilterRegion
import de.zwegen.zpaint.tools.options.FilterToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class FilterTool(
    private val filterToolOptionsView: FilterToolOptionsView?,
    private val selectedToolType: ZaintToolKind,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    private val onColorPickedListener: OnColorPickedListener? = null
) : ZaintToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {
    override val toolType: ZaintToolKind = selectedToolType
    override var drawTime: Long = 0L
    private val filterType = FilterType.fromToolType(selectedToolType)
    private var previewLayer: ZaintLayerContracts.ZaintLayer? = null
    private var previewOriginalBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private val previewScope = CoroutineScope(Dispatchers.Default)
    private val previewMutex = Mutex()
    private var previewJob: Job? = null
    private var previewGeneration = 0
    private var pendingValue: Int = DEFAULT_VALUE
    private val splashColors = mutableListOf(toolPaint.color)
    private val splashTolerances = mutableListOf(DEFAULT_SPLASH_TOLERANCE)
    private val splashAreas = mutableListOf<FilterArea>()
    private var activeSplashColorIndex = 0
    private var splashTapCoordinate: PointF? = null
    private var region = if (supportsAreaMode()) lastRegion else FilterRegion.TOTAL
    private var background = false
    private var autoTap: PointF? = null
    private val tapSlop by lazy { ViewConfiguration.get(contextCallback.context).scaledTouchSlop }
    private var motif: MotifSelection? = null
    private var motifRevision = 0L
    private var expectedLayerBitmap: Bitmap? = null
    private var expectedGeneration = 0
    private var center = PointF(0f, 0f)
    private var areaWidth = DEFAULT_AREA_SIZE
    private var areaHeight = DEFAULT_AREA_SIZE
    private var cornerRadius = DEFAULT_CORNER_RADIUS
    private var areaRotation = 0f
    private var activeGesture = AreaGesture.NONE
    private var resizeAction = ResizeAction.NONE
    private var roundnessDragStartY = 0f
    private var roundnessDragStartRadius = 0f
    private val transformUi = FloatingBoxTransformUi(contextCallback, workspace)
    private val boxResizeDelta = FloatingBoxResizeDelta()
    private val boxResizeApplication = FloatingBoxResizeApplication()
    private val boxRotation = FloatingBoxRotation()
    private val maskBounds = Rect()
    private val objectMaskPaint = neutralMaskPaint(inverse = false)
    private val backgroundMaskPaint = neutralMaskPaint(inverse = true)
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = AREA_STROKE_WIDTH
    }
    private val areaShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = AREA_SHADOW_STROKE_WIDTH
    }
    private val transformLinePaint = Paint().apply {
        color = contextCallback.getColor(R.color.zpaint_main_rectangle_tool_primary_color)
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    init {
        if (filterType == FilterType.INVERT) {
            applyFinalFilter(INVERT_VALUE)
        } else {
            restoreArea()
            if (filterType == FilterType.SPLASH) {
                splashAreas += currentArea()
            }
            configureOptions()
            startPreview()
            updatePreview(pendingValue)
        }
    }

    override fun draw(canvas: Canvas) {
        // At neutral strength there is no filtered image to indicate the target. Draw the
        // mask on the surface only; keep the layer and the inference source untouched.
        if (region == FilterRegion.AUTO && isNoEffect(pendingValue)) {
            val mask = motif?.mask ?: return
            if (!isCurrentPreviewSource()) return
            maskBounds.set(0, 0, mask.width, mask.height)
            canvas.drawBitmap(mask, null, maskBounds, if (background) backgroundMaskPaint else objectMaskPaint)
            return
        }
        if (!usesAreaMode()) {
            return
        }
        areaPaint.style = Paint.Style.STROKE
        areaShadowPaint.style = Paint.Style.STROKE
        areaPaint.strokeWidth = scaledStrokeWidth(AREA_STROKE_WIDTH)
        areaShadowPaint.strokeWidth = scaledStrokeWidth(AREA_SHADOW_STROKE_WIDTH)
        val bounds = areaBounds()
        canvas.save()
        canvas.rotate(areaRotation, center.x, center.y)
        // Use the same ordering as the shared Icon/Import transform UI: resize
        // markers behind the frame, rotation controls, frame, then markers on top.
        drawTransformResizeMarkers(canvas, bounds)
        drawTransformRotationArrows(canvas, bounds)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, areaShadowPaint)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, areaPaint)
        drawTransformResizeMarkers(canvas, bounds)
        drawRoundnessHandle(canvas, bounds)
        canvas.restore()
    }

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        if (region == FilterRegion.AUTO) {
            autoTap = PointF(coordinate.x, coordinate.y)
            return true
        }
        if (filterType == FilterType.SPLASH) {
            splashTapCoordinate = PointF(coordinate.x, coordinate.y)
            if (!usesAreaMode()) return true
            startPreview()
            activeGesture = resolveAreaGesture(coordinate)
            previousEventCoordinate = PointF(coordinate.x, coordinate.y)
            return true
        }
        if (!usesAreaMode()) {
            return false
        }
        if (hasPendingEffect()) {
            startPreview()
        }
        activeGesture = resolveAreaGesture(coordinate)
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        if (region == FilterRegion.AUTO) {
            if (!isAutoTap(coordinate)) autoTap = null
            return true
        }
        if (filterType == FilterType.SPLASH && splashTapCoordinate != null) {
            val tap = splashTapCoordinate ?: coordinate
            if (hypot(coordinate.x - tap.x, coordinate.y - tap.y) <= SPLASH_TAP_SLOP) {
                return true
            }
            splashTapCoordinate = null
        }
        if (!usesAreaMode()) {
            return false
        }
        val previous = previousEventCoordinate ?: coordinate
        val deltaX = coordinate.x - previous.x
        val deltaY = coordinate.y - previous.y
        when (activeGesture) {
            AreaGesture.MOVE -> {
                center.offset(deltaX, deltaY)
            }
            AreaGesture.ROTATE -> rotateArea(previous, deltaX, deltaY)
            AreaGesture.ROUNDNESS -> resizeRoundness(toAreaLocal(coordinate))
            AreaGesture.RESIZE -> resizeArea(deltaX, deltaY)
            AreaGesture.NONE -> Unit
        }
        keepAreaIntersectingCanvas()
        saveAreaState()
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    fun cancelMotifTap(): Boolean {
        autoTap = null
        return region == FilterRegion.AUTO
    }

    private fun isAutoTap(point: PointF): Boolean {
        val down = autoTap ?: return false
        return hypot(point.x - down.x, point.y - down.y) * workspace.scale <= tapSlop
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (region == FilterRegion.AUTO) {
            val tap = coordinate != null && isAutoTap(coordinate)
            autoTap = null
            if (tap && coordinate != null && workspace.contains(coordinate) && !commandManager.isBusy) {
                startPreview()
                val source = previewOriginalBitmap ?: return false
                if (coordinate.x >= 0f && coordinate.x < source.width &&
                    coordinate.y >= 0f && coordinate.y < source.height) {
                    if (motif != null && motifRevision != commandManager.revision) clearMotif()
                    motifRevision = commandManager.revision
                    val selection = motif ?: MotifSelection(contextCallback.context, ::motifChanged).also { motif = it }
                    selection.tap(source, coordinate.x.toInt(), coordinate.y.toInt())
                }
            }
            return true
        }
        if (filterType == FilterType.SPLASH) {
            splashTapCoordinate?.let { selectSplashColor(it) }
            splashTapCoordinate = null
        }
        if (usesAreaMode()) {
            activeGesture = AreaGesture.NONE
            resizeAction = ResizeAction.NONE
            saveAreaState()
            updatePreview(pendingValue)
        }
        return super.handleUp(coordinate)
    }

    override fun changePaintColor(color: Int, invalidate: Boolean) {
        super.changePaintColor(color, invalidate)
        if (filterType == FilterType.SPLASH) {
            splashColors[activeSplashColorIndex] = color
            publishSplashColors()
            startPreview()
            updatePreview(pendingValue)
        }
    }

    private fun configureOptions() {
        filterToolOptionsView ?: return
        when (filterType) {
            FilterType.BRIGHTNESS -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_brightness_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.CONTRAST -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_contrast_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.BLUR -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_blur_title)
                filterToolOptionsView.setRange(BLUR_MIN, BLUR_MAX, DEFAULT_VALUE)
            }
            FilterType.BLOOM -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_bloom_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, DEFAULT_VALUE)
            }
            FilterType.MEDIAN -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_median_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, DEFAULT_VALUE)
            }
            FilterType.BILATERAL -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_bilateral_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, DEFAULT_VALUE)
            }
            FilterType.SATURATION -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_saturation_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.HUE -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_hue_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.SPLASH -> {
                pendingValue = DEFAULT_SPLASH_TOLERANCE
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_splash_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, pendingValue)
                filterToolOptionsView.setSplashColorListener(object : FilterToolOptionsView.SplashColorListener {
                    override fun onAddSplashColor() {
                        if (splashColors.size == MAX_SPLASH_COLORS) return
                        saveAreaState()
                        splashColors += toolPaint.color
                        splashTolerances += pendingValue
                        splashAreas += currentArea()
                        activeSplashColorIndex = splashColors.lastIndex
                        setActiveSplashTolerance()
                        publishSplashColors()
                        updatePreview(pendingValue)
                    }

                    override fun onSplashColorSelected(index: Int) {
                        if (index !in splashColors.indices) return
                        saveAreaState()
                        activeSplashColorIndex = index
                        toolPaint.color = splashColors[index]
                        restoreActiveSplashArea()
                        setActiveSplashTolerance()
                        publishSplashColors()
                        workspace.invalidate()
                    }

                    override fun onRemoveSplashColor(index: Int) {
                        if (splashColors.size == MIN_SPLASH_COLORS || index !in splashColors.indices) return
                        saveAreaState()
                        splashColors.removeAt(index)
                        splashTolerances.removeAt(index)
                        splashAreas.removeAt(index)
                        activeSplashColorIndex = when {
                            index < activeSplashColorIndex -> activeSplashColorIndex - 1
                            index == activeSplashColorIndex -> index.coerceAtMost(splashColors.lastIndex)
                            else -> activeSplashColorIndex
                        }
                        toolPaint.color = splashColors[activeSplashColorIndex]
                        restoreActiveSplashArea()
                        setActiveSplashTolerance()
                        publishSplashColors()
                        updatePreview(pendingValue)
                    }
                })
                publishSplashColors()
            }
            FilterType.TEMPERATURE -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_temperature_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.HIGHLIGHTS -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_highlights_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.SHADOWS -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_shadows_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.SEPIA -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_sepia_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, DEFAULT_VALUE)
            }
            FilterType.SHARPEN -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_sharpen_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, DEFAULT_VALUE)
            }
            FilterType.VIGNETTE -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_vignette_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, DEFAULT_VALUE)
            }
            FilterType.CLARITY -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_clarity_title)
                filterToolOptionsView.setRange(PERCENT_MIN, PERCENT_MAX, DEFAULT_VALUE)
            }
            FilterType.EXPOSURE -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_exposure_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.VIBRANCE -> {
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_vibrance_title)
                filterToolOptionsView.setRange(SIGNED_MIN, SIGNED_MAX, DEFAULT_VALUE)
            }
            FilterType.PIXEL -> {
                pendingValue = DEFAULT_PIXEL_SIZE
                filterToolOptionsView.setTitle(R.string.pixel_tool_dialog_size_title)
                filterToolOptionsView.setRange(PIXEL_SIZE_MIN, PIXEL_SIZE_MAX, DEFAULT_PIXEL_SIZE)
            }
            FilterType.CONTOURS -> {
                pendingValue = DEFAULT_CONTOUR_TOLERANCE
                filterToolOptionsView.setTitle(R.string.filter_tool_dialog_contours_title)
                filterToolOptionsView.setRange(CONTOUR_TOLERANCE_MIN, CONTOUR_TOLERANCE_MAX, pendingValue)
            }
            FilterType.CONTOUR_ERASE -> Unit
            FilterType.INVERT -> Unit
        }
        if (filterType != FilterType.INVERT) {
            filterToolOptionsView.setAreaModeVisible(supportsAreaMode())
            if (supportsAreaMode()) {
                filterToolOptionsView.setRegion(region)
                publishAutoHint()
            }
            toolOptionsViewController.showCheckmark()
        }
        filterToolOptionsView.setCallback(object : FilterToolOptionsView.Callback {
            override fun onValueChanged(value: Int) {
                pendingValue = value
                if (filterType == FilterType.SPLASH) {
                    splashTolerances[activeSplashColorIndex] = value
                }
            }

            override fun onStartTracking() {
                startPreview()
            }

            override fun onStopTracking(value: Int) {
                pendingValue = value
                if (filterType == FilterType.SPLASH) {
                    splashTolerances[activeSplashColorIndex] = value
                }
                // Keyboard input has no drag-start event. Always make sure the source
                // bitmap for the preview exists before applying the new value.
                startPreview()
                updatePreview(value)
            }

            override fun setRegion(region: FilterRegion) {
                if (!supportsAreaMode()) return
                lastRegion = region
                val previous = this@FilterTool.region
                this@FilterTool.region = region
                if (region == FilterRegion.AUTO) {
                    background = previous == FilterRegion.AUTO && !background
                } else if (previous == FilterRegion.AUTO) {
                    clearMotif()
                }
                filterToolOptionsView.setRegion(region)
                publishAutoHint()
                saveAreaState()
                startPreview()
                updatePreview(pendingValue)
                workspace.invalidate()
            }
        })
    }

    private fun clearMotif() {
        autoTap = null
        val old = motif
        motif = null
        old?.clear()
        publishAutoHint()
    }

    private fun publishAutoHint() {
        if (region != FilterRegion.AUTO) return
        filterToolOptionsView?.setAutoHint(when {
            motif?.busy == true -> R.string.filter_auto_busy
            motif?.failed == true -> R.string.filter_auto_failed
            motif?.mask == null && background -> R.string.filter_auto_background_empty
            motif?.mask == null -> R.string.filter_auto_objects_empty
            background -> R.string.filter_auto_background
            else -> R.string.filter_auto_objects
        })
    }

    private fun motifChanged() {
        if (motif == null || region != FilterRegion.AUTO) return
        if (!isCurrentPreviewSource()) {
            releasePreview()
            return
        }
        publishAutoHint()
        updatePreview(pendingValue)
    }

    private fun isCurrentPreviewSource(): Boolean {
        val layer = previewLayer ?: return false
        if (motif != null && (commandManager.isBusy || commandManager.revision != motifRevision)) return false
        return workspace.layerModel.currentLayer === layer && ownsLayerBitmap(layer)
    }

    private fun ownsLayerBitmap(layer: ZaintLayerContracts.ZaintLayer): Boolean =
        layer.bitmap === expectedLayerBitmap && !layer.bitmap.isRecycled &&
            layer.bitmap.generationId == expectedGeneration

    private fun startPreview() {
        if (previewOriginalBitmap != null) {
            if (isCurrentPreviewSource()) return
            releasePreview()
        }
        val layer = workspace.layerModel.currentLayer ?: return
        previewLayer = layer
        previewOriginalBitmap = synchronized(workspace.layerModel) {
            expectedLayerBitmap = layer.bitmap
            expectedGeneration = layer.bitmap.generationId
            layer.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun updatePreview(value: Int) {
        val originalBitmap = previewOriginalBitmap ?: return
        val layer = previewLayer ?: return
        val mask = motif?.mask
        val auto = region == FilterRegion.AUTO
        if (auto && (mask == null || isNoEffect(value))) {
            cancelPendingPreview()
            if (previewBitmap != null && ownsLayerBitmap(layer)) {
                restoreOriginalBitmap(layer, originalBitmap)
            }
            workspace.invalidate()
            return
        }
        val generation = ++previewGeneration
        val previewSplashColors = splashColors.toIntArray()
        val previewSplashTolerances = splashTolerances.toIntArray()
        val useAreaMode = usesAreaMode()
        val inverse = background
        val previewSplashAreas: List<FilterArea>
        val previewArea: FilterArea
        if (useAreaMode) {
            keepAreaIntersectingCanvas()
            previewArea = currentArea()
            saveAreaState()
            previewSplashAreas = splashAreas.toList()
        } else {
            previewArea = FilterArea.circle(0f, 0f, 0f)
            previewSplashAreas = emptyList()
        }
        previewJob?.cancel()
        previewJob = previewScope.launch {
            previewMutex.withLock {
                var result: Bitmap? = null
                try {
                    result = if (isNoEffect(value)) {
                        originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    } else if (auto && mask != null) {
                        FilterProcessor.applyMotif(originalBitmap, filterType, value, previewSplashColors,
                            previewSplashTolerances, mask, inverse)
                    } else if (filterType == FilterType.SPLASH && useAreaMode) {
                        FilterProcessor.applySplashAreasPreview(
                            originalBitmap,
                            previewSplashColors,
                            previewSplashTolerances,
                            previewSplashAreas
                        )
                    } else if (filterType == FilterType.SPLASH) {
                        FilterProcessor.applySplashPreview(originalBitmap, previewSplashColors, previewSplashTolerances)
                    } else if (useAreaMode) {
                        FilterProcessor.applyAreaPreview(
                            originalBitmap,
                            filterType,
                            value,
                            previewArea
                        )
                    } else {
                        FilterProcessor.applyPreview(originalBitmap, filterType, value)
                    }
                    withContext(Dispatchers.Main) {
                        val previewStillCurrent = generation == previewGeneration &&
                            previewOriginalBitmap != null &&
                            previewLayer === layer && isCurrentPreviewSource()
                        if (previewStillCurrent) {
                            replacePreviewBitmap(layer, result ?: return@withContext)
                            result = null
                            workspace.invalidate()
                        }
                    }
                } finally {
                    result?.recycle()
                }
            }
        }
    }

    fun commitPendingFilter(): Boolean {
        if (region == FilterRegion.AUTO) {
            if (!isCurrentPreviewSource()) {
                releasePreview()
                return false
            }
            val selection = motif ?: return false
            val mask = selection.mask ?: return false
            if (selection.busy || !hasPendingEffect()) return false
            val command = MotifFilterCommand(filterType, pendingValue, splashColors.toIntArray(),
                splashTolerances.toIntArray(), mask, background)
            releasePreview()
            commandManager.addCommand(command)
            workspace.invalidate()
            return true
        }
        cancelPendingPreview()
        val value = pendingValue
        if (!hasPendingEffect()) {
            restorePreview()
            return false
        }
        val originalBitmap = previewOriginalBitmap
        val layer = previewLayer
        if (originalBitmap != null && layer != null) {
            restoreOriginalBitmap(layer, originalBitmap)
        }
        expectedLayerBitmap = null
        if (filterType == FilterType.CONTOURS && originalBitmap != null) {
            val contours = FilterProcessor.apply(originalBitmap, filterType, value)
            recycleAfterPreviewWork(originalBitmap)
            previewOriginalBitmap = null
            previewLayer = null
            commandManager.addCommand(ZaintCommandBatch().apply {
                addCommand(commandFactory.createFilterCommand(FilterType.CONTOUR_ERASE, value))
                addCommand(commandFactory.createAddEmptyLayerCommand())
                addCommand(
                    commandFactory.createClipboardCommand(
                        contours,
                        PointF(workspace.width / 2f, workspace.height / 2f),
                        workspace.width.toFloat(),
                        workspace.height.toFloat(),
                        0f
                    )
                )
            })
            workspace.invalidate()
            return true
        }
        recycleAfterPreviewWork(originalBitmap)
        if (filterType == FilterType.SPLASH) {
            previewOriginalBitmap = null
            previewLayer = null
            if (usesAreaMode()) {
                keepAreaIntersectingCanvas()
                saveAreaState()
                commandManager.addCommand(
                    commandFactory.createAreaSplashCommand(
                        splashColors.toIntArray(),
                        splashTolerances.toIntArray(),
                        splashAreas.toList()
                    )
                )
            } else {
                commandManager.addCommand(
                    commandFactory.createSplashCommand(splashColors.toIntArray(), splashTolerances.toIntArray())
                )
            }
            workspace.invalidate()
            return true
        }
        previewOriginalBitmap = null
        previewLayer = null
        return if (usesAreaMode()) {
            keepAreaIntersectingCanvas()
            commandManager.addCommand(commandFactory.createAreaFilterCommand(filterType, value, currentArea()))
            workspace.invalidate()
            true
        } else {
            applyFinalFilter(value)
            true
        }
    }

    fun finishPendingFilterOnNavigation() {
        restorePreview()
    }

    private fun restorePreview() {
        releasePreview()
        pendingValue = DEFAULT_VALUE
        workspace.invalidate()
    }

    private fun releasePreview() {
        cancelPendingPreview()
        clearMotif()
        val originalBitmap = previewOriginalBitmap
        val layer = previewLayer
        if (originalBitmap != null && layer != null && previewBitmap != null && ownsLayerBitmap(layer)) {
            restoreOriginalBitmap(layer, originalBitmap)
        }
        recycleAfterPreviewWork(originalBitmap)
        previewBitmap = null
        previewOriginalBitmap = null
        previewLayer = null
        expectedLayerBitmap = null
    }

    private fun applyFinalFilter(value: Int) {
        commandManager.addCommand(commandFactory.createFilterCommand(filterType, value))
        workspace.invalidate()
    }

    private fun cancelPendingPreview() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
    }

    /**
     * The surface renderer locks the layer model while drawing. Use the same lock when replacing
     * its bitmap so a bitmap is never recycled while Canvas still references it.
     */
    private fun replacePreviewBitmap(layer: ZaintLayerContracts.ZaintLayer, bitmap: Bitmap) {
        synchronized(workspace.layerModel) {
            val previousPreview = previewBitmap
            layer.bitmap = bitmap
            previewBitmap = bitmap
            expectedLayerBitmap = bitmap
            expectedGeneration = bitmap.generationId
            recycleBitmap(previousPreview)
        }
    }

    private fun restoreOriginalBitmap(layer: ZaintLayerContracts.ZaintLayer, original: Bitmap) {
        val restoredBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        synchronized(workspace.layerModel) {
            val previousPreview = previewBitmap
            layer.bitmap = restoredBitmap
            expectedLayerBitmap = restoredBitmap
            expectedGeneration = restoredBitmap.generationId
            previewBitmap = null
            recycleBitmap(previousPreview)
        }
    }

    /** Waits for a cancelled preview calculation before recycling the bitmap it read from. */
    private fun recycleAfterPreviewWork(bitmap: Bitmap?) {
        if (bitmap == null) return
        previewScope.launch {
            previewMutex.withLock { recycleBitmap(bitmap) }
        }
    }

    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }

    internal fun supportsAreaMode(): Boolean = filterType != FilterType.INVERT && filterType != FilterType.CONTOURS

    private fun usesAreaMode(): Boolean = supportsAreaMode() && region == FilterRegion.AREA

    private fun hasPendingEffect(): Boolean = filterType == FilterType.SPLASH || !isNoEffect(pendingValue)

    private fun isNoEffect(value: Int): Boolean = when (filterType) {
        FilterType.SPLASH -> false
        FilterType.PIXEL -> value == PIXEL_NEUTRAL_VALUE
        else -> value == DEFAULT_VALUE
    }

    private fun selectSplashColor(coordinate: PointF) {
        if (!workspace.contains(coordinate)) return
        val source = previewOriginalBitmap ?: workspace.bitmapOfAllLayers ?: return
        val x = coordinate.x.toInt().coerceIn(0, source.width - 1)
        val y = coordinate.y.toInt().coerceIn(0, source.height - 1)
        val color = source.getPixel(x, y)
        onColorPickedListener?.colorChanged(color)
        changePaintColor(color)
    }

    private fun publishSplashColors() {
        filterToolOptionsView?.setSplashColors(splashColors, activeSplashColorIndex)
    }

    private fun setActiveSplashTolerance() {
        pendingValue = splashTolerances[activeSplashColorIndex]
        filterToolOptionsView?.setRange(PERCENT_MIN, PERCENT_MAX, pendingValue)
    }

    private fun restoreActiveSplashArea() {
        val area = splashAreas.getOrNull(activeSplashColorIndex) ?: return
        center = PointF(area.centerX, area.centerY)
        areaWidth = area.width.coerceAtLeast(MIN_AREA_SIZE)
        areaHeight = area.height.coerceAtLeast(MIN_AREA_SIZE)
        cornerRadius = area.cornerRadius.coerceIn(0f, min(areaWidth, areaHeight) / 2f)
        areaRotation = area.rotationDegrees
        keepAreaIntersectingCanvas()
        saveAreaState()
    }

    private fun restoreArea() {
        val savedX = lastAreaCenterX
        val savedY = lastAreaCenterY
        if (savedX == null || savedY == null) {
            resetArea()
            saveAreaState()
            return
        }
        center = PointF(savedX, savedY)
        areaWidth = lastAreaWidth.coerceAtLeast(MIN_AREA_SIZE)
        areaHeight = lastAreaHeight.coerceAtLeast(MIN_AREA_SIZE)
        cornerRadius = lastAreaCornerRadius.coerceIn(0f, min(areaWidth, areaHeight) / 2f)
        areaRotation = lastAreaRotation
        keepAreaIntersectingCanvas()
    }

    private fun resetArea() {
        center = PointF(workspace.width / 2f, workspace.height / 2f)
        val defaultSize = min(workspace.width, workspace.height) * DEFAULT_AREA_SIZE_FACTOR
        areaWidth = defaultSize.coerceAtLeast(MIN_AREA_SIZE)
        areaHeight = areaWidth
        cornerRadius = areaWidth / 2f
        areaRotation = 0f
    }

    private fun currentArea(): FilterArea =
        FilterArea(center.x, center.y, areaWidth, areaHeight, cornerRadius, areaRotation).normalized(MIN_AREA_SIZE)

    private fun areaBounds(): RectF = RectF(
        center.x - areaWidth / 2f,
        center.y - areaHeight / 2f,
        center.x + areaWidth / 2f,
        center.y + areaHeight / 2f
    )

    private fun keepAreaIntersectingCanvas() {
        areaWidth = areaWidth.coerceAtLeast(MIN_AREA_SIZE)
        areaHeight = areaHeight.coerceAtLeast(MIN_AREA_SIZE)
        cornerRadius = cornerRadius.coerceIn(0f, min(areaWidth, areaHeight) / 2f)
        val area = currentArea()
        center.x = center.x.coerceIn(-area.boundingHalfWidth, workspace.width + area.boundingHalfWidth)
        center.y = center.y.coerceIn(-area.boundingHalfHeight, workspace.height + area.boundingHalfHeight)
    }

    private fun drawTransformResizeMarkers(canvas: Canvas, bounds: RectF) {
        canvas.save()
        canvas.translate(bounds.centerX(), bounds.centerY())
        transformLinePaint.color = contextCallback.getColor(R.color.zpaint_main_rectangle_tool_primary_color)
        transformLinePaint.style = Paint.Style.STROKE
        transformUi.drawResizeMarkers(
            canvas,
            bounds.width(),
            bounds.height(),
            shrinking = 0,
            paint = transformLinePaint
        )
        canvas.restore()
    }

    private fun drawTransformRotationArrows(canvas: Canvas, bounds: RectF) {
        canvas.save()
        canvas.translate(bounds.centerX(), bounds.centerY())
        transformUi.drawRotationArrows(canvas, bounds.width(), bounds.height())
        canvas.restore()
    }

    private fun drawRoundnessHandle(canvas: Canvas, bounds: RectF) {
        val handleRadius = textGripSize(ROUNDNESS_GRIP_RADIUS)
        val handleX = bounds.centerX()
        val handleY = bounds.top - textGripSize(ROUNDNESS_GRIP_DISTANCE)
        transformLinePaint.apply {
            color = contextCallback.getColor(R.color.zpaint_main_rectangle_tool_primary_color)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(handleX, handleY, handleRadius, transformLinePaint)
        transformLinePaint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = textGripStrokeWidth()
        }
        canvas.drawCircle(handleX, handleY, handleRadius, transformLinePaint)
        canvas.drawLine(handleX, bounds.top, handleX, handleY + handleRadius, transformLinePaint)
        transformLinePaint.style = Paint.Style.STROKE
    }

    private fun resolveAreaGesture(coordinate: PointF): AreaGesture {
        val bounds = areaBounds()
        val localCoordinate = toAreaLocal(coordinate)
        val roundingHandleX = bounds.centerX()
        val roundingHandleY = bounds.top - textGripSize(ROUNDNESS_GRIP_DISTANCE)
        val roundnessHitRadius = textGripSize(ROUNDNESS_GRIP_RADIUS * ROUNDNESS_GRIP_HIT_MULTIPLIER)
        if (hypot(localCoordinate.x - roundingHandleX, localCoordinate.y - roundingHandleY) <= roundnessHitRadius) {
            roundnessDragStartY = localCoordinate.y
            roundnessDragStartRadius = cornerRadius
            return AreaGesture.ROUNDNESS
        }
        val interaction = transformUi.resolve(
            coordinate.x,
            coordinate.y,
            center.x,
            center.y,
            areaWidth,
            areaHeight,
            areaRotation,
            rotationEnabled = true
        )
        resizeAction = interaction.resizeAction
        return when (interaction.action) {
            FloatingBoxAction.MOVE -> AreaGesture.MOVE
            FloatingBoxAction.RESIZE -> AreaGesture.RESIZE
            FloatingBoxAction.ROTATE -> AreaGesture.ROTATE
            else -> AreaGesture.NONE
        }
    }

    private fun resizeArea(deltaX: Float, deltaY: Float) {
        val previous = FloatingBoxResizeState(center.x, center.y, areaWidth, areaHeight)
        val movement = boxResizeDelta.resolve(
            deltaX,
            deltaY,
            areaRotation,
            resizeAction,
            areaWidth,
            areaHeight,
            keepAspectRatioOnCorners = false
        )
        val resized = boxResizeApplication.apply(
            previous,
            movement,
            resizeAction,
            workspace.width * MAXIMUM_BORDER_RATIO,
            workspace.height * MAXIMUM_BORDER_RATIO,
            limitBorderRatio = true
        )
        center.set(
            if (resized.width < MIN_AREA_SIZE) previous.centerX else resized.centerX,
            if (resized.height < MIN_AREA_SIZE) previous.centerY else resized.centerY
        )
        areaWidth = resized.width.coerceAtLeast(MIN_AREA_SIZE)
        areaHeight = resized.height.coerceAtLeast(MIN_AREA_SIZE)
    }

    private fun resizeRoundness(coordinate: PointF) {
        val maximumRadius = min(areaWidth, areaHeight) / 2f
        val dragDistance = textGripSize(ROUNDNESS_DRAG_DISTANCE)
        cornerRadius = (roundnessDragStartRadius +
            (roundnessDragStartY - coordinate.y) * maximumRadius / dragDistance)
            .coerceIn(0f, maximumRadius)
    }

    private fun rotateArea(previous: PointF, deltaX: Float, deltaY: Float) {
        areaRotation = boxRotation.afterDrag(
            previous.x,
            previous.y,
            deltaX,
            deltaY,
            center.x,
            center.y,
            areaRotation
        )
    }

    private fun toAreaLocal(coordinate: PointF): PointF {
        val radians = Math.toRadians(areaRotation.toDouble())
        val offsetX = coordinate.x - center.x
        val offsetY = coordinate.y - center.y
        return PointF(
            (center.x + offsetX * Math.cos(radians) + offsetY * Math.sin(radians)).toFloat(),
            (center.y - offsetX * Math.sin(radians) + offsetY * Math.cos(radians)).toFloat()
        )
    }

    private fun scaledStrokeWidth(defaultStrokeWidth: Float): Float =
        defaultStrokeWidth / workspace.scale.coerceAtLeast(MIN_SCALE_FOR_DRAWING)

    private fun textGripSize(defaultSize: Float): Float =
        defaultSize * contextCallback.displayMetrics.density / workspace.scale

    private fun textGripStrokeWidth(): Float =
        (3f * contextCallback.displayMetrics.density / workspace.scale).coerceIn(1f, 8f)

    private fun saveAreaState() {
        if (filterType == FilterType.SPLASH && activeSplashColorIndex in splashAreas.indices) {
            splashAreas[activeSplashColorIndex] = currentArea()
        }
        lastAreaCenterX = center.x
        lastAreaCenterY = center.y
        lastAreaWidth = areaWidth
        lastAreaHeight = areaHeight
        lastAreaCornerRadius = cornerRadius
        lastAreaRotation = areaRotation
    }

    override fun resetInternalState(stateChange: Tool.StateChange) {
        if (stateChange == Tool.StateChange.NEW_IMAGE_LOADED) {
            discardPreviewForNewImage()
            clearSavedAreaState()
            restoreArea()
        } else if (stateChange == Tool.StateChange.MOVE_CANCELED) {
            autoTap = null
        } else if (region == FilterRegion.AUTO) {
            releasePreview()
        }
    }

    override fun resetInternalState() {
        restorePreview()
    }

    /**
     * A document load replaces the layer stack. A filter preview made for the previous document
     * must never be restored into, or sampled from, the newly loaded image.
     */
    private fun discardPreviewForNewImage() {
        cancelPendingPreview()
        clearMotif()
        expectedLayerBitmap = null
        recycleAfterPreviewWork(previewOriginalBitmap)
        val pendingPreview = previewBitmap
        previewOriginalBitmap = null
        previewBitmap = null
        previewLayer = null
        recycleAfterPreviewWork(pendingPreview)
    }

    private enum class AreaGesture {
        NONE, MOVE, RESIZE, ROUNDNESS, ROTATE
    }

    companion object {
        // Tint the target blue. In background mode the target is the complement of mask alpha.
        private fun neutralMaskPaint(inverse: Boolean) = Paint().apply {
            val overlayAlpha = 112f
            val alphaScale = overlayAlpha / 255f
            colorFilter = ColorMatrixColorFilter(floatArrayOf(
                0f, 0f, 0f, 0f, 45f,
                0f, 0f, 0f, 0f, 180f,
                0f, 0f, 0f, 0f, 255f,
                0f, 0f, 0f, if (inverse) -alphaScale else alphaScale, if (inverse) overlayAlpha else 0f
            ))
        }

        private const val SIGNED_MIN = -100
        private const val SIGNED_MAX = 100
        private const val BLUR_MIN = 0
        private const val BLUR_MAX = 25
        private const val PERCENT_MIN = 0
        private const val PERCENT_MAX = 100
        private const val DEFAULT_VALUE = 0
        private const val INVERT_VALUE = 0
        private const val MIN_AREA_SIZE = 16f
        private const val DEFAULT_AREA_SIZE = 160f
        private const val DEFAULT_CORNER_RADIUS = 80f
        private const val DEFAULT_AREA_SIZE_FACTOR = 0.5f
        private const val PIXEL_SIZE_MIN = 0
        private const val PIXEL_SIZE_MAX = 50
        private const val DEFAULT_PIXEL_SIZE = 0
        private const val PIXEL_NEUTRAL_VALUE = 0
        private const val DEFAULT_SPLASH_TOLERANCE = 18
        private const val SPLASH_TAP_SLOP = 12f
        private const val MIN_SPLASH_COLORS = 1
        private const val MAX_SPLASH_COLORS = 4
        private const val CONTOUR_TOLERANCE_MIN = 0
        private const val CONTOUR_TOLERANCE_MAX = 255
        private const val DEFAULT_CONTOUR_TOLERANCE = 160
        private const val AREA_STROKE_WIDTH = 3f
        private const val AREA_SHADOW_STROKE_WIDTH = 7f
        private const val ROUNDNESS_GRIP_DISTANCE = 30f
        private const val ROUNDNESS_GRIP_RADIUS = 12f
        private const val ROUNDNESS_GRIP_HIT_MULTIPLIER = 1.5f
        private const val ROUNDNESS_DRAG_DISTANCE = 180f
        private const val MIN_SCALE_FOR_DRAWING = 0.1f
        private var lastRegion = FilterRegion.AUTO
        private var lastAreaCenterX: Float? = null
        private var lastAreaCenterY: Float? = null
        private var lastAreaWidth = DEFAULT_AREA_SIZE
        private var lastAreaHeight = DEFAULT_AREA_SIZE
        private var lastAreaCornerRadius = DEFAULT_CORNER_RADIUS
        private var lastAreaRotation = 0f

        private fun clearSavedAreaState() {
            lastAreaCenterX = null
            lastAreaCenterY = null
            lastAreaWidth = DEFAULT_AREA_SIZE
            lastAreaHeight = DEFAULT_AREA_SIZE
            lastAreaCornerRadius = DEFAULT_CORNER_RADIUS
            lastAreaRotation = 0f
        }
    }
}
