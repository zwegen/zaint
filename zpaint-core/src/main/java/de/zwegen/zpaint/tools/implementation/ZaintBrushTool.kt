package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathDashPathEffect
import android.graphics.PointF
import android.graphics.RectF
import android.view.View
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.common.ZaintBrushOptionsListener
import de.zwegen.zpaint.tools.common.CommonBrushPreviewListener
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.tools.options.BrushPreset.BRUSH
import de.zwegen.zpaint.tools.options.BrushPreset.CALLIGRAPHY
import de.zwegen.zpaint.tools.options.BrushPreset.MARKER
import de.zwegen.zpaint.tools.options.BrushPreset.PENCIL
import de.zwegen.zpaint.tools.options.BrushPreset.ARROW
import de.zwegen.zpaint.tools.options.BrushPresetChangeListener
import de.zwegen.zpaint.tools.options.BrushSoftnessChangeListener
import de.zwegen.zpaint.tools.options.ZaintBrushOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.viewholder.BottomNavigationViewHolder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val MINIMUM_STROKE_DISTANCE = 5f
private const val PENCIL_SMOOTHING_MAX = 0.55f
private const val PENCIL_SMOOTHING_MIN = 0.18f
private const val PENCIL_SMOOTHING_FAST_MOVEMENT_DISTANCE = 16f

private const val MARKER_WIDTH_FACTOR = 1f
private const val PENCIL_DEFAULT_STROKE_WIDTH = 12f
private const val BRUSH_DEFAULT_STROKE_WIDTH = 12f
private const val MARKER_DEFAULT_STROKE_WIDTH = 42f
private const val CALLIGRAPHY_DEFAULT_STROKE_WIDTH = 18f
private const val ARROW_DEFAULT_STROKE_WIDTH = 12f
private const val ARROW_HEAD_MIN_LENGTH_FACTOR = 6f
private const val ARROW_HEAD_MAX_LENGTH_FACTOR = 8f
private const val ARROW_HEAD_MIN_ANGLE_DEGREES = 45f
private const val ARROW_HEAD_MAX_ANGLE_DEGREES = 60f
private const val ARROW_HEAD_CURVE_FACTOR = 0.035f
private const val ARROW_DIRECTION_LAST_SEGMENT_FRACTION = 0.21f
private const val CALLIGRAPHY_STROKE_FACTOR = 0.12f
private const val CALLIGRAPHY_ADVANCE_FACTOR = 0.22f
private const val CALLIGRAPHY_NIB_SHORT_FACTOR = 0.16f
private const val CALLIGRAPHY_NIB_LONG_FACTOR = 0.95f
private const val CALLIGRAPHY_NIB_ROTATION = -35f
private const val SOFT_BRUSH_BLUR_FACTOR = 0.28f
private const val SOFTNESS_MAX = 100
private const val NEON_MARKER_COLOR = 0x40B6FF00
private const val ROYAL_BLUE_COLOR = 0xFF4169E1.toInt()

internal fun softBrushUsesDirectLayerClear(color: Int): Boolean = color ushr 24 == 0

open class ZaintBrushTool(
    val brushToolOptionsView: ZaintBrushOptions,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long,
    initialBrushPreset: BrushPreset = BRUSH,
    initialBrushStrokeWidths: Map<BrushPreset, Float> = defaultBrushStrokeWidths(),
    private val useSharedBrushPresetState: Boolean = false,
    private val bottomNavigationViewHolder: BottomNavigationViewHolder? = null,
    private val onPipetteSelected: (() -> Unit)? = null,
    private val addToColorHistory: (Int) -> Unit = {}
) : ZaintToolBase(contextCallback, toolOptionsViewController, toolPaint, workspace, idlingResource, commandManager),
    BrushPresetChangeListener,
    BrushSoftnessChangeListener {
    protected open val previewPaint: Paint
        get() = toolPaint.previewPaint

    protected open val bitmapPaint: Paint
        get() = toolPaint.paint

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.BRUSH

    @JvmField
    var pathToDraw: ZaintStrokePath = ZaintStrokePath()
    private val pathLock = Any()
    var initialEventCoordinate: PointF? = null
    private var pathInsideBitmap = false
    private val drawToolMovedDistance = PointF(0f, 0f)
    private var previousPencilInput: PointF? = null
    private var pencilInputBeforePrevious: PointF? = null

    val pointArray = mutableListOf<PointF>()
    private var pickColorWithBrush = false
    private var isPickingColorWithBrush = false
    private var surfaceBitmap: Bitmap? = null
    private var onPipetteColorPicked: ((Int) -> Unit)? = null
    private var lastPickedColor: Int? = null
    private var brushPreset = if (useSharedBrushPresetState) sharedBrushPreset.takeUnless { it == BRUSH } ?: PENCIL else initialBrushPreset
    private var pencilColor = if (useSharedBrushPresetState) sharedPencilColorState else toolPaint.color
    private var brushColor = if (useSharedBrushPresetState) sharedBrushColorState else toolPaint.color
    private var markerColor = if (useSharedBrushPresetState) markerColorState else NEON_MARKER_COLOR
    private var calligraphyColor = if (useSharedBrushPresetState) calligraphyColorState else ROYAL_BLUE_COLOR
    private var arrowColor = if (useSharedBrushPresetState) arrowColorState else Color.BLACK
    private val brushStrokeWidths =
        if (useSharedBrushPresetState) sharedBrushStrokeWidths.toMutableMap() else initialBrushStrokeWidths.toMutableMap()
    private val brushSoftness =
        if (useSharedBrushPresetState) sharedBrushSoftness.toMutableMap() else defaultBrushSoftness().toMutableMap()

    init {
        toolOptionsViewController.enable()
        pathToDraw.incReserve(1)
        brushToolOptionsView.setListener(ZaintBrushOptionsListener(this))
        brushToolOptionsView.setPreviewState(
            CommonBrushPreviewListener(
                toolPaint,
                toolType
            )
        )
        brushToolOptionsView.showPaint(toolPaint.paint)
        brushToolOptionsView.showStrokeCap(toolPaint.strokeCap)
        brushToolOptionsView.showPreset(brushPreset)
        syncActivePresetColor()
        applyBrushPreset()
        brushToolOptionsView.showStrokeWidth(activeBrushStrokeWidth().toInt())
        showActiveBrushSoftness()
        updateColorButton()
    }

    override fun draw(canvas: Canvas) {
        if (pickColorWithBrush || isPickingColorWithBrush) {
            return
        }
        val pathSnapshot = getPathSnapshot()
        val previewPaintSnapshot = Paint(previewPaint)
        canvas.run {
            save()
            clipRect(0, 0, workspace.width, workspace.height)
            if (usesSoftRendering()) {
                drawPath(pathSnapshot, softBrushPaint(previewPaintSnapshot))
            } else {
                drawPath(pathSnapshot, previewPaintSnapshot)
            }
            restore()
        }
    }

    private fun showBrushSpecificLayoutOnHandleUp() {
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
        if (pickColorWithBrush) {
            isPickingColorWithBrush = true
            return pickBrushColor(coordinate)
        }
        super.handleDown(coordinate)
        initialEventCoordinate = PointF(coordinate.x, coordinate.y)
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        previousPencilInput = PointF(coordinate.x, coordinate.y)
        pencilInputBeforePrevious = null
        synchronized(pathLock) {
            pathToDraw.moveTo(coordinate.x, coordinate.y)
        }
        drawToolMovedDistance.set(0f, 0f)
        pointArray.add(PointF(coordinate.x, coordinate.y))
        pathInsideBitmap = workspace.contains(coordinate)
        return true
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        if (pickColorWithBrush) {
            return
        }
    }

    override fun handleUpAnimations(coordinate: PointF?) {
        if (isPickingColorWithBrush) {
            return
        }
        showBrushSpecificLayoutOnHandleUp()
        super.handleUp(coordinate)
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        if (isPickingColorWithBrush) {
            return pickBrushColor(coordinate)
        }
        if (eventCoordinatesAreNull() || coordinate == null) {
            return false
        }
        super.handleMove(coordinate, shouldAnimate)
        val drawCoordinate = smoothedDrawCoordinate(coordinate)
        previousEventCoordinate?.let {
            val midpointX = (it.x + drawCoordinate.x) / 2f
            val midpointY = (it.y + drawCoordinate.y) / 2f
            synchronized(pathLock) {
                pathToDraw.quadTo(it.x, it.y, midpointX, midpointY)
                pathToDraw.incReserve(1)
            }
            drawToolMovedDistance.set(
                drawToolMovedDistance.x + abs(coordinate.x - it.x),
                drawToolMovedDistance.y + abs(coordinate.y - it.y)
            )
            pointArray.add(
                if (brushPreset == ARROW) PointF(coordinate.x, coordinate.y)
                else PointF(drawCoordinate.x, drawCoordinate.y)
            )
            it.set(drawCoordinate.x, drawCoordinate.y)
        }
        if (!pathInsideBitmap && workspace.contains(coordinate)) {
            pathInsideBitmap = true
        }
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (isPickingColorWithBrush) {
            val didPickColor = pickBrushColor(coordinate, saveToHistory = true)
            val pickedColor = lastPickedColor
            pickColorWithBrush = false
            isPickingColorWithBrush = false
            surfaceBitmap = null
            brushToolOptionsView.showPipetteActive(false)
            onPipetteColorPicked?.takeIf { didPickColor }?.let { callback ->
                pickedColor?.let(callback)
            }
            onPipetteColorPicked = null
            lastPickedColor = null
            return didPickColor
        }
        if (eventCoordinatesAreNull() || coordinate == null) {
            return false
        }
        showBrushSpecificLayoutOnHandleUp()
        super.handleUp(coordinate)

        if (!pathInsideBitmap && workspace.contains(coordinate)) {
            pathInsideBitmap = true
        }

        previousEventCoordinate?.let {
            drawToolMovedDistance.set(
                drawToolMovedDistance.x + abs(coordinate.x - it.x),
                drawToolMovedDistance.y + abs(coordinate.y - it.y)
            )
        }

        return if (MINIMUM_STROKE_DISTANCE < max(drawToolMovedDistance.x, drawToolMovedDistance.y)) {
            addPathCommand(coordinate)
        } else {
            initialEventCoordinate?.let {
                return addPointCommand(it)
            }
            false
        }
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun resetInternalState() {
        synchronized(pathLock) {
            pathToDraw.rewind()
        }
        pointArray.clear()
        pickColorWithBrush = false
        isPickingColorWithBrush = false
        surfaceBitmap = null
        onPipetteColorPicked = null
        lastPickedColor = null
        brushToolOptionsView.showPipetteActive(false)
        initialEventCoordinate = null
        previousEventCoordinate = null
        previousPencilInput = null
        pencilInputBeforePrevious = null
    }

    override fun changePaintColor(color: Int, invalidate: Boolean) {
        setActivePresetColor(color)
        super.changePaintColor(color, false)
        applyBrushPreset()
        persistBrushPresetState()
        updateColorButton()
        if (invalidate) brushToolOptionsView.refreshPreview()
    }

    fun changePaintColorFromColorPicker(color: Int) {
        setActivePresetColorExactly(color)
        super.changePaintColor(color, false)
        applyBrushPreset()
        persistBrushPresetState()
        updateColorButton()
        brushToolOptionsView.refreshPreview()
    }

    override fun changePaintStrokeWidth(strokeWidth: Int) {
        super.changePaintStrokeWidth(strokeWidth)
        brushStrokeWidths[brushPreset] = strokeWidth.toFloat()
        applyBrushPreset()
        persistBrushPresetState()
    }

    override fun changeBrushSoftness(softness: Int) {
        if (!activePresetSupportsSoftness()) return
        brushSoftness[brushPreset] = softness.coerceIn(0, SOFTNESS_MAX)
        persistBrushPresetState()
        brushToolOptionsView.refreshPreview()
    }

    override fun changeBrushPreset(brushPreset: BrushPreset) {
        this.brushPreset = if (useSharedBrushPresetState && brushPreset == BRUSH) PENCIL else brushPreset
        syncActivePresetColor()
        addToColorHistory(activePresetColor())
        applyBrushPreset()
        persistBrushPresetState()
        updateColorButton()
        brushToolOptionsView.showPreset(this.brushPreset)
        brushToolOptionsView.showStrokeWidth(activeBrushStrokeWidth().toInt())
        showActiveBrushSoftness()
        brushToolOptionsView.refreshPreview()
    }

    override fun selectPipette() = selectPipette(null)

    fun preservesTransparencyWhenSelectingAColor(): Boolean = brushPreset == MARKER

    fun selectPipette(onColorPicked: ((Int) -> Unit)?) {
        synchronized(pathLock) {
            pathToDraw.rewind()
        }
        pointArray.clear()
        initialEventCoordinate = null
        previousEventCoordinate = null
        previousPencilInput = null
        pencilInputBeforePrevious = null
        pickColorWithBrush = true
        isPickingColorWithBrush = false
        surfaceBitmap = workspace.bitmapOfAllLayers
        onPipetteColorPicked = onColorPicked
        lastPickedColor = null
        brushToolOptionsView.showPipetteActive(true)
    }

    private fun pickBrushColor(coordinate: PointF?, saveToHistory: Boolean = false): Boolean {
        if (coordinate == null || !workspace.contains(coordinate)) {
            return false
        }
        val bitmap = surfaceBitmap ?: workspace.bitmapOfAllLayers?.also {
            surfaceBitmap = it
        } ?: return false
        val color = bitmap.getPixel(coordinate.x.toInt(), coordinate.y.toInt())
        lastPickedColor = color
        changePaintColor(color)
        if (saveToHistory) {
            addToColorHistory(color)
        }
        return true
    }

    private fun applyBrushPreset() {
        val activeColor = activePresetColor()
        toolPaint.color = activeColor
        val paint = toolPaint.paint
        val baseAlpha = Color.alpha(activeColor)
        paint.pathEffect = null
        paint.maskFilter = null
        paint.style = Paint.Style.STROKE
        paint.strokeMiter = 10f
        paint.strokeWidth = activeBrushStrokeWidth()
        paint.color = activeColor
        paint.alpha = baseAlpha

        when (brushPreset) {
            PENCIL -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.isAntiAlias = false
            }
            BRUSH -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.isAntiAlias = true
            }
            MARKER -> {
                paint.color = markerColor
                paint.strokeWidth = activeBrushStrokeWidth() * MARKER_WIDTH_FACTOR
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.ROUND
                paint.alpha = baseAlpha
                paint.isAntiAlias = true
            }
            CALLIGRAPHY -> {
                paint.pathEffect = calligraphyPathEffect()
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.strokeWidth = max(1f, activeBrushStrokeWidth() * CALLIGRAPHY_STROKE_FACTOR)
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeMiter = 2f
                paint.isAntiAlias = true
            }
            ARROW -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.isAntiAlias = false
            }
        }
        toolPaint.paint = Paint(paint)
    }

    private fun activePresetColor(): Int =
        when (brushPreset) {
            MARKER -> markerColor
            CALLIGRAPHY -> calligraphyColor
            PENCIL -> pencilColor
            BRUSH -> brushColor
            ARROW -> arrowColor
        }

    private fun setActivePresetColor(color: Int) {
        when (brushPreset) {
            MARKER -> markerColor = colorWithIndependentAlpha(color, markerColor)
            CALLIGRAPHY -> calligraphyColor = colorWithIndependentAlpha(color, calligraphyColor)
            PENCIL -> pencilColor = colorWithIndependentAlpha(color, pencilColor)
            BRUSH -> brushColor = colorWithIndependentAlpha(color, brushColor)
            ARROW -> arrowColor = colorWithIndependentAlpha(color, arrowColor)
        }
    }

    private fun setActivePresetColorExactly(color: Int) {
        when (brushPreset) {
            MARKER -> markerColor = color
            CALLIGRAPHY -> calligraphyColor = color
            PENCIL -> pencilColor = color
            BRUSH -> brushColor = color
            ARROW -> arrowColor = color
        }
    }

    private fun colorWithIndependentAlpha(color: Int, currentColor: Int, maxAlpha: Int = 255): Int {
        val currentAlpha = Color.alpha(currentColor).coerceAtMost(maxAlpha)
        val incomingAlpha = Color.alpha(color).coerceAtMost(maxAlpha)
        val alpha = if (sameRgb(color, currentColor)) incomingAlpha else currentAlpha
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun sameRgb(firstColor: Int, secondColor: Int): Boolean =
        Color.red(firstColor) == Color.red(secondColor) &&
            Color.green(firstColor) == Color.green(secondColor) &&
            Color.blue(firstColor) == Color.blue(secondColor)

    private fun syncActivePresetColor() {
        toolPaint.color = activePresetColor()
    }

    private fun updateColorButton() {
        bottomNavigationViewHolder?.setColorButtonColor(activePresetColor())
    }

    private fun persistBrushPresetState() {
        if (!useSharedBrushPresetState) {
            return
        }
        sharedBrushPreset = brushPreset
        sharedPencilColorState = pencilColor
        sharedBrushColorState = brushColor
        markerColorState = markerColor
        calligraphyColorState = calligraphyColor
        arrowColorState = arrowColor
        sharedBrushStrokeWidths.clear()
        sharedBrushStrokeWidths.putAll(brushStrokeWidths)
        sharedBrushSoftness.clear()
        sharedBrushSoftness.putAll(brushSoftness)
    }

    private fun activeBrushStrokeWidth(): Float =
        brushStrokeWidths[brushPreset] ?: BRUSH_DEFAULT_STROKE_WIDTH

    private fun activePresetSupportsSoftness(): Boolean = brushPreset == PENCIL || brushPreset == ARROW

    private fun activeBrushSoftness(): Int = when (brushPreset) {
        BRUSH -> SOFTNESS_MAX
        PENCIL, ARROW -> brushSoftness[brushPreset] ?: 0
        else -> 0
    }

    private fun usesSoftRendering(): Boolean = activeBrushSoftness() > 0

    private fun showActiveBrushSoftness() {
        brushToolOptionsView.showBrushSoftness(
            visible = activePresetSupportsSoftness(),
            softness = activeBrushSoftness()
        )
    }

    private fun calligraphyPathEffect(): PathDashPathEffect {
        val brushStrokeWidth = activeBrushStrokeWidth()
        val shortSide = max(1f, brushStrokeWidth * CALLIGRAPHY_NIB_SHORT_FACTOR)
        val longSide = max(shortSide + 1f, brushStrokeWidth * CALLIGRAPHY_NIB_LONG_FACTOR)
        val nib = Path().apply {
            addRoundRect(
                RectF(-shortSide, -longSide, shortSide, longSide),
                shortSide,
                shortSide,
                Path.Direction.CW
            )
            transform(Matrix().apply { setRotate(CALLIGRAPHY_NIB_ROTATION) })
        }
        val advance = max(1f, brushStrokeWidth * CALLIGRAPHY_ADVANCE_FACTOR)
        return PathDashPathEffect(nib, advance, 0f, PathDashPathEffect.Style.TRANSLATE)
    }

    private fun softBrushPaint(source: Paint): Paint =
        Paint(source).apply {
            val brushStrokeWidth = activeBrushStrokeWidth()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = brushStrokeWidth
            maskFilter = BlurMaskFilter(softBrushBlurRadius(), BlurMaskFilter.Blur.NORMAL)
            isAntiAlias = true
        }

    /** Applies a blurred clear stroke directly to the active layer instead of creating an empty stamp. */
    private fun softBrushEraserPaint(): Paint =
        softBrushPaint(Paint(bitmapPaint)).apply {
            color = Color.TRANSPARENT
            alpha = 0
            shader = null
            xfermode = toolPaint.eraseXfermode
        }

    private fun softBrushBlurRadius(): Float =
        max(1f, activeBrushStrokeWidth() * SOFT_BRUSH_BLUR_FACTOR * activeBrushSoftness() / SOFTNESS_MAX)

    private fun eventCoordinatesAreNull(): Boolean =
        initialEventCoordinate == null || previousEventCoordinate == null

    /**
     * Brush strokes filter the small hand wobble between touch samples. Fast movement and clear
     * changes of direction get less filtering, so lines still follow deliberate strokes.
     */
    private fun smoothedDrawCoordinate(coordinate: PointF): PointF {
        val previous = previousPencilInput
        val beforePrevious = pencilInputBeforePrevious
        val smoothed = if (previous == null || beforePrevious == null) {
            PointF(coordinate.x, coordinate.y)
        } else {
            val movementX = coordinate.x - previous.x
            val movementY = coordinate.y - previous.y
            val movementDistance = kotlin.math.hypot(movementX, movementY)
            var smoothing = (PENCIL_SMOOTHING_MAX - movementDistance / PENCIL_SMOOTHING_FAST_MOVEMENT_DISTANCE)
                .coerceIn(PENCIL_SMOOTHING_MIN, PENCIL_SMOOTHING_MAX)
            val incomingX = previous.x - beforePrevious.x
            val incomingY = previous.y - beforePrevious.y
            val incomingLength = kotlin.math.hypot(incomingX, incomingY)
            if (incomingLength > 0f && movementDistance > 0f) {
                val directionChange = (incomingX * movementX + incomingY * movementY) /
                    (incomingLength * movementDistance)
                if (directionChange < 0.5f) smoothing = PENCIL_SMOOTHING_MIN
            }
            val averageX = (beforePrevious.x + previous.x * 2f + coordinate.x) / 4f
            val averageY = (beforePrevious.y + previous.y * 2f + coordinate.y) / 4f
            PointF(
                coordinate.x + (averageX - coordinate.x) * smoothing,
                coordinate.y + (averageY - coordinate.y) * smoothing
            )
        }
        pencilInputBeforePrevious = previous?.let { PointF(it.x, it.y) }
        previousPencilInput = PointF(coordinate.x, coordinate.y)
        return smoothed
    }

    private fun addPathCommand(coordinate: PointF): Boolean {
        val pathSnapshot = synchronized(pathLock) {
            pathToDraw.lineTo(coordinate.x, coordinate.y)
            if (brushPreset == ARROW) {
                appendArrowHead(
                    path = pathToDraw,
                    anchor = arrowHeadAnchor(coordinate),
                    directionEndpoint = coordinate
                )
            }
            ZaintStrokePath(pathToDraw)
        }

        if (!pathInsideBitmap) {
            resetInternalState(StateChange.RESET_INTERNAL_STATE)
            return false
        }

        val command = createBrushPathCommand(pathSnapshot)
        commandManager.addCommand(command)

        pointArray.clear()
        return true
    }

    private fun createBrushPathCommand(pathSnapshot: ZaintStrokePath): Command {
        if (!usesSoftRendering()) {
            return commandFactory.createPathCommand(Paint(bitmapPaint), pathSnapshot)
        }

        if (softBrushUsesDirectLayerClear(activePresetColor())) {
            return commandFactory.createPathCommand(softBrushEraserPaint(), pathSnapshot)
        }

        val bounds = softBrushBounds(pathSnapshot)
        val brushBitmap = createSoftBrushBitmap(pathSnapshot, bounds)
        return commandFactory.createClipboardCommand(
            brushBitmap,
            softBrushBitmapCenter(bounds),
            bounds.width.toFloat(),
            bounds.height.toFloat(),
            0f
        ).also {
            brushBitmap.recycle()
        }
    }

    private fun createSoftBrushBitmap(pathSnapshot: ZaintStrokePath, bounds: SoftBrushStrokeBounds): Bitmap =
        Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                translate(-bounds.left.toFloat(), -bounds.top.toFloat())
                drawPath(pathSnapshot, softBrushPaint(Paint(bitmapPaint)))
            }
        }

    private fun softBrushBounds(path: ZaintStrokePath): SoftBrushStrokeBounds {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        return SoftBrushStrokeBounds.calculate(
            workspace.width,
            workspace.height,
            pathBounds.left,
            pathBounds.top,
            pathBounds.right,
            pathBounds.bottom,
            activeBrushStrokeWidth(),
            softBrushBlurRadius()
        )
    }

    private fun softBrushBounds(point: PointF): SoftBrushStrokeBounds =
        SoftBrushStrokeBounds.calculate(
            workspace.width,
            workspace.height,
            point.x,
            point.y,
            point.x,
            point.y,
            activeBrushStrokeWidth(),
            softBrushBlurRadius()
        )

    private fun softBrushBitmapCenter(bounds: SoftBrushStrokeBounds): PointF =
        PointF(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)

    private fun getPathSnapshot(): ZaintStrokePath =
        synchronized(pathLock) {
            ZaintStrokePath(pathToDraw)
        }

    private fun addPointCommand(coordinate: PointF): Boolean {
        if (!pathInsideBitmap) {
            resetInternalState(StateChange.RESET_INTERNAL_STATE)
            return false
        }

        pointArray.clear()
        val command = createBrushPointCommand(coordinate)
        commandManager.addCommand(command)
        return true
    }

    private fun createBrushPointCommand(coordinate: PointF): Command {
        if (!usesSoftRendering()) {
            return commandFactory.createPointCommand(bitmapPaint, coordinate)
        }

        if (softBrushUsesDirectLayerClear(activePresetColor())) {
            return commandFactory.createPointCommand(softBrushEraserPaint(), coordinate)
        }

        val bounds = softBrushBounds(coordinate)
        val brushBitmap = createSoftBrushPointBitmap(coordinate, bounds)
        return commandFactory.createClipboardCommand(
            brushBitmap,
            softBrushBitmapCenter(bounds),
            bounds.width.toFloat(),
            bounds.height.toFloat(),
            0f
        ).also {
            brushBitmap.recycle()
        }
    }

    private fun createSoftBrushPointBitmap(coordinate: PointF, bounds: SoftBrushStrokeBounds): Bitmap =
        Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                translate(-bounds.left.toFloat(), -bounds.top.toFloat())
                drawPoint(coordinate.x, coordinate.y, softBrushPaint(bitmapPaint))
            }
        }

    private fun arrowHeadAnchor(endpoint: PointF): PointF {
        if (workspace.contains(endpoint)) {
            return endpoint
        }

        val lastVisiblePoint = pointArray.asReversed().firstOrNull(workspace::contains)
            ?: initialEventCoordinate?.takeIf(workspace::contains)
            ?: return endpoint
        val deltaX = endpoint.x - lastVisiblePoint.x
        val deltaY = endpoint.y - lastVisiblePoint.y
        if (deltaX == 0f && deltaY == 0f) {
            return lastVisiblePoint
        }

        var fraction = 1f
        if (deltaX > 0f) {
            fraction = min(fraction, (workspace.width - lastVisiblePoint.x) / deltaX)
        } else if (deltaX < 0f) {
            fraction = min(fraction, -lastVisiblePoint.x / deltaX)
        }
        if (deltaY > 0f) {
            fraction = min(fraction, (workspace.height - lastVisiblePoint.y) / deltaY)
        } else if (deltaY < 0f) {
            fraction = min(fraction, -lastVisiblePoint.y / deltaY)
        }
        return PointF(
            lastVisiblePoint.x + deltaX * fraction.coerceIn(0f, 1f),
            lastVisiblePoint.y + deltaY * fraction.coerceIn(0f, 1f)
        )
    }

    private fun appendArrowHead(
        path: ZaintStrokePath,
        anchor: PointF,
        directionEndpoint: PointF
    ) {
        val directionStart = pointAtStartOfArrowDirectionSegment(directionEndpoint)
        val deltaX = directionEndpoint.x - directionStart.x
        val deltaY = directionEndpoint.y - directionStart.y
        if (deltaX == 0f && deltaY == 0f) {
            return
        }

        val direction = atan2(deltaY, deltaX).toDouble()
        val halfAngle = Math.toRadians(
            Random.nextDouble(ARROW_HEAD_MIN_ANGLE_DEGREES.toDouble(), ARROW_HEAD_MAX_ANGLE_DEGREES.toDouble()) / 2
        )
        val strokeWidth = activeBrushStrokeWidth()
        val firstHeadLength = strokeWidth * Random.nextDouble(
            ARROW_HEAD_MIN_LENGTH_FACTOR.toDouble(),
            ARROW_HEAD_MAX_LENGTH_FACTOR.toDouble()
        ).toFloat()
        val secondHeadLength = strokeWidth * Random.nextDouble(
            ARROW_HEAD_MIN_LENGTH_FACTOR.toDouble(),
            ARROW_HEAD_MAX_LENGTH_FACTOR.toDouble()
        ).toFloat()
        appendArrowSide(path, anchor, direction + Math.PI - halfAngle, firstHeadLength, 1f)
        appendArrowSide(path, anchor, direction + Math.PI + halfAngle, secondHeadLength, -1f)
    }

    private fun pointAtStartOfArrowDirectionSegment(endpoint: PointF): PointF {
        val strokePoints = pointArray.toMutableList().apply {
            if (lastOrNull()?.let { it.x != endpoint.x || it.y != endpoint.y } != false) {
                add(PointF(endpoint.x, endpoint.y))
            }
        }
        if (strokePoints.size < 2) {
            return initialEventCoordinate ?: endpoint
        }

        val lengths = strokePoints.zipWithNext { first, second ->
            kotlin.math.hypot(second.x - first.x, second.y - first.y)
        }
        val totalLength = lengths.sum()
        if (totalLength <= 0f) {
            return strokePoints.first()
        }

        val targetDistance = totalLength * (1f - ARROW_DIRECTION_LAST_SEGMENT_FRACTION)
        var completedLength = 0f
        for (index in lengths.indices) {
            val segmentLength = lengths[index]
            if (completedLength + segmentLength >= targetDistance && segmentLength > 0f) {
                val fraction = (targetDistance - completedLength) / segmentLength
                val first = strokePoints[index]
                val second = strokePoints[index + 1]
                return PointF(
                    first.x + (second.x - first.x) * fraction,
                    first.y + (second.y - first.y) * fraction
                )
            }
            completedLength += segmentLength
        }
        return strokePoints[strokePoints.lastIndex - 1]
    }

    private fun appendArrowSide(
        path: ZaintStrokePath,
        endpoint: PointF,
        angle: Double,
        length: Float,
        curveDirection: Float
    ) {
        val endX = endpoint.x + cos(angle).toFloat() * length
        val endY = endpoint.y + sin(angle).toFloat() * length
        val curveOffset = length * ARROW_HEAD_CURVE_FACTOR * curveDirection
        val controlX = (endpoint.x + endX) / 2f - sin(angle).toFloat() * curveOffset
        val controlY = (endpoint.y + endY) / 2f + cos(angle).toFloat() * curveOffset
        path.moveTo(endpoint.x, endpoint.y)
        path.quadTo(controlX, controlY, endX, endY)
    }

    companion object {
        private var sharedBrushPreset = PENCIL
        private var sharedPencilColorState = Color.BLACK
        private var sharedBrushColorState = Color.RED
        private var markerColorState = NEON_MARKER_COLOR
        private var calligraphyColorState = ROYAL_BLUE_COLOR
        private var arrowColorState = Color.BLACK
        private val sharedBrushStrokeWidths = defaultBrushStrokeWidths().toMutableMap()
        private val sharedBrushSoftness = defaultBrushSoftness().toMutableMap()

        fun updateSharedActivePresetColor(color: Int) {
            when (sharedBrushPreset) {
                MARKER -> markerColorState = colorWithSharedIndependentAlpha(color, markerColorState)
                CALLIGRAPHY -> calligraphyColorState = colorWithSharedIndependentAlpha(color, calligraphyColorState)
                PENCIL -> sharedPencilColorState = colorWithSharedIndependentAlpha(color, sharedPencilColorState)
                BRUSH -> sharedBrushColorState = colorWithSharedIndependentAlpha(color, sharedBrushColorState)
                ARROW -> arrowColorState = colorWithSharedIndependentAlpha(color, arrowColorState)
            }
        }

        private fun colorWithSharedIndependentAlpha(color: Int, currentColor: Int, maxAlpha: Int = 255): Int {
            val currentAlpha = Color.alpha(currentColor).coerceAtMost(maxAlpha)
            val incomingAlpha = Color.alpha(color).coerceAtMost(maxAlpha)
            val alpha = if (sameSharedRgb(color, currentColor)) incomingAlpha else currentAlpha
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }

        private fun sameSharedRgb(firstColor: Int, secondColor: Int): Boolean =
            Color.red(firstColor) == Color.red(secondColor) &&
                Color.green(firstColor) == Color.green(secondColor) &&
                Color.blue(firstColor) == Color.blue(secondColor)

        fun defaultBrushStrokeWidths(): Map<BrushPreset, Float> =
            mapOf(
                PENCIL to PENCIL_DEFAULT_STROKE_WIDTH,
                BRUSH to BRUSH_DEFAULT_STROKE_WIDTH,
                MARKER to MARKER_DEFAULT_STROKE_WIDTH,
                CALLIGRAPHY to CALLIGRAPHY_DEFAULT_STROKE_WIDTH,
                ARROW to ARROW_DEFAULT_STROKE_WIDTH
            )

        fun defaultBrushSoftness(): Map<BrushPreset, Int> =
            mapOf(
                PENCIL to 0,
                ARROW to 0
            )

    }
}
