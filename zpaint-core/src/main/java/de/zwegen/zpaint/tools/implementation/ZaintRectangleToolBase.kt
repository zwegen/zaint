package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.CountDownTimer
import android.util.DisplayMetrics
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.VisibleForTesting
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.common.INVALID_RESOURCE_ID
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ContextCallback.ScreenOrientation
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
const val MAXIMUM_BORDER_RATIO = 2f

@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
const val MINIMAL_BOX_SIZE = 3

const val DEFAULT_BOX_RESIZE_MARGIN = 20

const val DEFAULT_ANTIALIASING_ON = true
private const val DEFAULT_RECTANGLE_MARGIN = 100f
private const val DEFAULT_TOOL_STROKE_WIDTH = 3f
private const val MINIMAL_TOOL_STROKE_WIDTH = 1f
private const val MAXIMAL_TOOL_STROKE_WIDTH = 8f
private const val DEFAULT_ROTATION_SYMBOL_DISTANCE = 20
private const val DEFAULT_ROTATION_SYMBOL_WIDTH = 30
private const val DEFAULT_MAXIMUM_BOX_RESOLUTION = 0f
private const val DEFAULT_RECTANGLE_SHRINKING = 0
private const val HIGHLIGHT_RECTANGLE_SHRINKING = 5
private const val DEFAULT_ROTATION_ENABLED = false
private const val DEFAULT_RESIZE_POINTS_VISIBLE = true
private const val DEFAULT_RESPECT_MAXIMUM_BORDER_RATIO = true
private const val DEFAULT_RESPECT_MAXIMUM_BOX_RESOLUTION = false
internal const val CLICK_TIMEOUT_MILLIS = 250L
private const val CONSTANT_2 = 8
internal const val CONSTANT_3 = 3
private const val BUNDLE_BOX_WIDTH = "BOX_WIDTH"
private const val BUNDLE_BOX_HEIGHT = "BOX_HEIGHT"
private const val BUNDLE_BOX_ROTATION = "BOX_ROTATION"

enum class FloatingBoxShape {
    RECTANGLE,
    OVAL
}

abstract class ZaintRectangleToolBase(
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline
) : ZaintShapeToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {
    private val boxHitTest = FloatingBoxHitTest()
    private val boxInteractionResolver = FloatingBoxInteractionResolver()
    private val boxResizeDelta = FloatingBoxResizeDelta()
    private val boxResizeApplication = FloatingBoxResizeApplication()
    private val rotationArrowArcStrokeWidth: Int
    private val rotationArrowArcRadius: Int
    private val rotationArrowHeadSize: Int
    private val rotationArrowOffset: Int
    private val arcPaint: Paint
    private val arrowPaint: Paint
    private val arcPath: Path
    private val arrowPath: Path
    private val tempDrawingRectangle: RectF
    private val tempToolPosition: PointF
    private val tempShapePath: Path

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @JvmField
    var boxWidth: Float

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @JvmField
    var boxHeight: Float

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @JvmField
    var boxRotation = 0f // in degree

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @JvmField
    var rotationEnabled: Boolean

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @JvmField
    var drawingBitmap: Bitmap? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @JvmField
    var rotationSymbolDistance = 0f

    @JvmField
    var toolStrokeWidth = 0f

    @JvmField
    var maximumBoxResolution: Float

    @JvmField
    var resizePointsVisible: Boolean

    @JvmField
    var respectMaximumBorderRatio: Boolean

    @JvmField
    var respectMaximumBoxResolution: Boolean

    @JvmField
    var rectangleShrinkingOnHighlight: Int

    @JvmField
    var shouldDrawRectangle = true

    @JvmField
    var floatingBoxShape = FloatingBoxShape.RECTANGLE

    protected open val keepAspectRatioOnCornerResize: Boolean = true

    private var boxResizeMargin: Float? = 0f
    private var rotationSymbolWidth: Float? = 0f
    private var currentAction: FloatingBoxAction? = null
    private var overlayDrawable: Drawable? = null
    private var downTimer: CountDownTimer? = null
    private var resizeAction: ResizeAction
    private var touchDownPositionX = 0f
    private var touchDownPositionY = 0f
    private var shapeSizeChangedListener: ShapeSizeChangedListener? = null
    private val boxRotationResolver = FloatingBoxRotation()
    private val boxSizeLimits = FloatingBoxSizeLimits(MINIMAL_BOX_SIZE.toFloat())

    init {
        val orientation = contextCallback.orientation
        val boxSize =
            if (orientation == ScreenOrientation.PORTRAIT) metrics.widthPixels.toFloat() else metrics.heightPixels.toFloat()
        boxWidth = boxSize / workspace.scale - 2 * getInverselyProportionalSizeForZoom(
            DEFAULT_RECTANGLE_MARGIN
        )
        boxHeight = boxWidth
        if (DEFAULT_RESPECT_MAXIMUM_BORDER_RATIO && (
            boxHeight > workspace.height * MAXIMUM_BORDER_RATIO ||
                boxWidth > workspace.width * MAXIMUM_BORDER_RATIO
            )
        ) {
            boxHeight = workspace.height * MAXIMUM_BORDER_RATIO
            boxWidth = workspace.width * MAXIMUM_BORDER_RATIO
        }
        rectangleShrinkingOnHighlight = DEFAULT_RECTANGLE_SHRINKING
        rotationArrowArcStrokeWidth = getDensitySpecificValue(2)
        rotationArrowArcRadius = getDensitySpecificValue(CONSTANT_2)
        rotationArrowHeadSize = getDensitySpecificValue(CONSTANT_3)
        rotationArrowOffset = getDensitySpecificValue(CONSTANT_3)
        resizeAction = ResizeAction.NONE
        rotationEnabled = DEFAULT_ROTATION_ENABLED
        resizePointsVisible = DEFAULT_RESIZE_POINTS_VISIBLE
        respectMaximumBorderRatio = DEFAULT_RESPECT_MAXIMUM_BORDER_RATIO
        respectMaximumBoxResolution = DEFAULT_RESPECT_MAXIMUM_BOX_RESOLUTION
        maximumBoxResolution = DEFAULT_MAXIMUM_BOX_RESOLUTION

        initScaleDependedValues()
        createOverlayDrawable()
        linePaint.apply {
            reset()
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        arcPaint = Paint()
        arcPaint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Cap.BUTT
        }
        arrowPaint = Paint()
        arrowPaint.color = Color.WHITE
        arrowPaint.style = Paint.Style.FILL
        arcPath = Path()
        arrowPath = Path()
        tempDrawingRectangle = RectF()
        tempToolPosition = PointF()
        tempShapePath = Path()
    }

    private fun getDensitySpecificValue(value: Int): Int {
        val baseDensity = DisplayMetrics.DENSITY_MEDIUM
        val density = max(DisplayMetrics.DENSITY_MEDIUM, metrics.densityDpi)
        return value * density / baseDensity
    }

    private fun initScaleDependedValues() {
        toolStrokeWidth = getStrokeWidthForZoom(
            DEFAULT_TOOL_STROKE_WIDTH,
            MINIMAL_TOOL_STROKE_WIDTH, MAXIMAL_TOOL_STROKE_WIDTH
        )
        boxResizeMargin = getInverselyProportionalSizeForZoom(DEFAULT_BOX_RESIZE_MARGIN.toFloat())
        rotationSymbolDistance = getInverselyProportionalSizeForZoom(
            DEFAULT_ROTATION_SYMBOL_DISTANCE.toFloat()
        ) * 2
        rotationSymbolWidth =
            getInverselyProportionalSizeForZoom(DEFAULT_ROTATION_SYMBOL_WIDTH.toFloat())
    }

    private fun <T : Any, R : Any> ifNotNull(vararg options: T?, block: (List<T>) -> R) {
        if (options.all { it != null }) {
            block(options.filterNotNull())
        }
    }

    fun setBitmap(bitmap: Bitmap?) {
        if (bitmap != null) {
            drawingBitmap = bitmap
        }
        workspace.invalidate()
    }

     fun showToolSpecificLayout() {
         if (!toolOptionsViewController.isVisible) {
             when (this) {
                 is IconTool -> changeIconToolLayoutVisibility(false)
                 is ZaintClipboardTool -> changeClipboardToolLayoutVisibility(false)
                 else -> if (this !is ZaintTextTool &&
                     toolOptionsViewController.toolSpecificOptionsLayout.visibility == View.INVISIBLE) {
                     toolOptionsViewController.slideUp(
                         toolOptionsViewController.toolSpecificOptionsLayout,
                         willHide = false,
                         showOptionsView = true
                     )
                 }
             }
         }
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        movedDistance.set(0f, 0f)

        coordinate?.apply {
            previousEventCoordinate = PointF(x, y)
            val interaction = boxInteractionResolver.resolve(
                x,
                y,
                toolPosition.x,
                toolPosition.y,
                boxWidth,
                boxHeight,
                boxRotation,
                boxResizeMargin ?: 0f,
                rotationEnabled,
                rotationSymbolDistance
            )
            currentAction = interaction.action
            resizeAction = interaction.resizeAction
        }
        touchDownPositionX = toolPosition.x
        touchDownPositionY = toolPosition.y
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        if (previousEventCoordinate == null || currentAction == null) {
            return false
        }
        ifNotNull(coordinate, previousEventCoordinate) { (coordinate, previousEventCoordinate) ->
            val delta = PointF(
                coordinate.x - previousEventCoordinate.x,
                coordinate.y - previousEventCoordinate.y
            )
            movedDistance.set(movedDistance.x + abs(delta.x), movedDistance.y + abs(delta.y))
            previousEventCoordinate.set(coordinate.x, coordinate.y)
            when (currentAction) {
                FloatingBoxAction.MOVE -> move(delta.x, delta.y)
                FloatingBoxAction.RESIZE -> resize(delta.x, delta.y)
                FloatingBoxAction.ROTATE -> rotate(delta.x, delta.y)
                else -> Unit
            }
        }
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
            toggleShapeSizeVisibility(false)
        if (previousEventCoordinate == null) {
            return false
        }
        showToolSpecificLayout()
        ifNotNull(coordinate, previousEventCoordinate) { (coordinate, previousEventCoordinate) ->
            movedDistance.x += abs(coordinate.x - previousEventCoordinate.x)
            movedDistance.y += abs(coordinate.y - previousEventCoordinate.y)
        }
        return true
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    fun boxContainsPoint(coordinate: PointF): Boolean {
        return boxHitTest.contains(
            coordinate.x,
            coordinate.y,
            toolPosition.x,
            toolPosition.y,
            boxWidth,
            boxHeight,
            boxRotation
        )
    }

    protected fun isMovingFloatingBox(): Boolean =
        currentAction == FloatingBoxAction.MOVE

    protected fun isRotatingFloatingBox(): Boolean =
        currentAction == FloatingBoxAction.ROTATE

    protected fun boxIntersectsWorkspace(): Boolean =
        toolPosition.x - boxWidth / 2 < workspace.width && toolPosition.y - boxHeight / 2 < workspace.height && toolPosition.x + boxWidth / 2 >= 0 && toolPosition.y + boxHeight / 2 >= 0

    override fun draw(canvas: Canvas) {
        drawShape(canvas)
    }

    override fun drawShape(canvas: Canvas) {
        initScaleDependedValues()
        val boxWidth = boxWidth
        val boxHeight = boxHeight
        val boxRotation = boxRotation
        tempToolPosition.set(toolPosition.x, toolPosition.y)
        canvas.run {
            save()
            translate(tempToolPosition.x, tempToolPosition.y)
            rotate(boxRotation)
        }
        if (resizePointsVisible) {
            drawToolSpecifics(canvas, boxWidth, boxHeight)
        }
        if (rotationEnabled) {
            drawRotationArrows(canvas, boxWidth, boxHeight)
        }
        drawBitmap(canvas, boxWidth, boxHeight)
        if (overlayDrawable != null) {
            drawOverlayDrawable(canvas, boxWidth, boxHeight, boxRotation)
        }
        if (shouldDrawRectangle) {
            drawRectangle(canvas, boxWidth, boxHeight)
        }
        drawToolSpecifics(canvas, boxWidth, boxHeight)
        canvas.restore()
    }

    private fun drawRotationArrows(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        FloatingBoxTransformControls.drawRotationArrows(
            canvas,
            boxWidth,
            boxHeight,
            getInverselyProportionalSizeForZoom(rotationArrowArcStrokeWidth.toFloat()),
            getInverselyProportionalSizeForZoom(rotationArrowArcRadius.toFloat()),
            getInverselyProportionalSizeForZoom(rotationArrowHeadSize.toFloat()),
            getInverselyProportionalSizeForZoom(rotationArrowOffset.toFloat()),
            arcPaint,
            arrowPaint,
            arcPath,
            arrowPath,
            tempDrawingRectangle
        )
    }

    protected open fun drawBitmap(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        drawingBitmap?.let {
            tempDrawingRectangle.set(-boxWidth / 2, -boxHeight / 2, boxWidth / 2, boxHeight / 2)
            if (floatingBoxShape == FloatingBoxShape.OVAL) {
                tempShapePath.reset()
                tempShapePath.addOval(tempDrawingRectangle, Path.Direction.CW)
                canvas.clipPath(tempShapePath)
            } else {
                canvas.clipRect(tempDrawingRectangle)
            }

            val alphaPaint = Paint().apply {
                workspace.layerModel.currentLayer?.let { layer ->
                    alpha = layer.getValueForOpacityPercentage()
                }
            }

            canvas.drawBitmap(it, null, tempDrawingRectangle, alphaPaint)
        }
    }

    private fun drawOverlayDrawable(
        canvas: Canvas,
        boxWidth: Float,
        boxHeight: Float,
        boxRotation: Float
    ) {
        val size = (min(boxWidth, boxHeight) / CONSTANT_2).toInt()
        canvas.save()
        canvas.rotate(-boxRotation)
        overlayDrawable?.run {
            setBounds(-size, -size, size, size)
            draw(canvas)
        }
        canvas.restore()
    }

    private fun drawRectangle(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        linePaint.strokeWidth = toolStrokeWidth
        linePaint.color = secondaryShapeColor
        tempDrawingRectangle.set(
            -boxWidth / 2 + rectangleShrinkingOnHighlight,
            -boxHeight / 2 + rectangleShrinkingOnHighlight,
            boxWidth / 2 - rectangleShrinkingOnHighlight,
            boxHeight / 2 - rectangleShrinkingOnHighlight
        )
        if (floatingBoxShape == FloatingBoxShape.OVAL) {
            canvas.drawOval(tempDrawingRectangle, linePaint)
        } else {
            canvas.drawRect(tempDrawingRectangle, linePaint)
        }
    }

    private fun move(deltaX: Float, deltaY: Float) {
        toolPosition.x += deltaX
        toolPosition.y += deltaY
    }

    private fun rotate(deltaX: Float, deltaY: Float) {
        previousEventCoordinate?.let {
            val rawRotation = boxRotationResolver.afterDrag(
                it.x,
                it.y,
                deltaX,
                deltaY,
                toolPosition.x,
                toolPosition.y,
                rotationReferenceForDrag()
            )
            boxRotation = resolveBoxRotationAfterDrag(rawRotation)
        }
    }

    /** Lets specialized tools retain a free rotation while displaying a snapped one. */
    protected open fun rotationReferenceForDrag(): Float = boxRotation

    protected open fun resolveBoxRotationAfterDrag(rawRotation: Float): Float = rawRotation

    private fun resize(deltaX: Float, deltaY: Float) {
        toggleShapeSizeVisibility(true)
        val movement = boxResizeDelta.resolve(
            deltaX,
            deltaY,
            boxRotation,
            resizeAction,
            boxWidth,
            boxHeight,
            keepAspectRatioOnCornerResize
        )
        val oldPosX = toolPosition.x
        val oldPosY = toolPosition.y
        val oldHeight = boxHeight
        val oldWidth = boxWidth

        val resized = boxResizeApplication.apply(
            FloatingBoxResizeState(toolPosition.x, toolPosition.y, boxWidth, boxHeight),
            movement,
            resizeAction,
            workspace.width * MAXIMUM_BORDER_RATIO,
            workspace.height * MAXIMUM_BORDER_RATIO,
            respectMaximumBorderRatio
        )
        toolPosition.x = resized.centerX
        toolPosition.y = resized.centerY
        boxWidth = resized.width
        boxHeight = resized.height

        // prevent that box gets too small
        if (boxSizeLimits.isBelowMinimum(boxWidth)) {
            boxWidth = boxSizeLimits.minimumSize()
            toolPosition.x = oldPosX
        }
        if (boxSizeLimits.isBelowMinimum(boxHeight)) {
            boxHeight = boxSizeLimits.minimumSize()
            toolPosition.y = oldPosY
        }
        if (boxSizeLimits.shouldRestoreForResolution(
                boxWidth,
                boxHeight,
                respectMaximumBoxResolution,
                maximumBoxResolution
            )
        ) {
            preventThatBoxGetsTooLarge(oldWidth, oldHeight, oldPosX, oldPosY)
        }
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    protected open fun preventThatBoxGetsTooLarge(
        oldWidth: Float,
        oldHeight: Float,
        oldPosX: Float,
        oldPosY: Float
    ) {
        boxWidth = oldWidth
        boxHeight = oldHeight
        toolPosition.x = oldPosX
        toolPosition.y = oldPosY
    }

    private fun createOverlayDrawable() {
        val overlayDrawableResource = toolType.overlayDrawableResource
        if (overlayDrawableResource != INVALID_RESOURCE_ID) {
            overlayDrawable = contextCallback.getDrawable(overlayDrawableResource)
            overlayDrawable?.isFilterBitmap = false
        }
    }

    fun highlightBox() {
        downTimer = object :
            CountDownTimer(
                CLICK_TIMEOUT_MILLIS,
                CLICK_TIMEOUT_MILLIS / CONSTANT_3
            ) {
            override fun onTick(millisUntilFinished: Long) {
                highlightBoxWhenClickInBox(true)
                workspace.invalidate()
            }

            override fun onFinish() {
                highlightBoxWhenClickInBox(false)
                workspace.invalidate()
                downTimer?.cancel()
            }
        }.start()
    }

    fun highlightBoxWhenClickInBox(highlight: Boolean) {
        @ColorRes val colorId =
            if (highlight) R.color.zpaint_main_rectangle_tool_highlight_color else R.color.zpaint_main_rectangle_tool_accent_color
        secondaryShapeColor = contextCallback.getColor(colorId)
        rectangleShrinkingOnHighlight =
            if (highlight) HIGHLIGHT_RECTANGLE_SHRINKING else DEFAULT_RECTANGLE_SHRINKING
    }

    override fun getAutoScrollDirection(
        pointX: Float,
        pointY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Point {
        return if (currentAction == FloatingBoxAction.MOVE || currentAction == FloatingBoxAction.RESIZE) {
            super.getAutoScrollDirection(pointX, pointY, screenWidth, screenHeight)
        } else Point(0, 0)
    }

    override fun onSaveInstanceState(bundle: Bundle?) {
        super.onSaveInstanceState(bundle)
        bundle?.apply {
            putFloat(BUNDLE_BOX_WIDTH, boxWidth)
            putFloat(BUNDLE_BOX_HEIGHT, boxHeight)
            putFloat(BUNDLE_BOX_ROTATION, boxRotation)
        }
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        bundle?.apply {
            boxWidth = getFloat(BUNDLE_BOX_WIDTH, boxWidth)
            boxHeight = getFloat(BUNDLE_BOX_HEIGHT, boxHeight)
            boxRotation = getFloat(BUNDLE_BOX_ROTATION, boxRotation)
        }
    }

    override fun drawToolSpecifics(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        linePaint.color = primaryShapeColor
        FloatingBoxTransformControls.drawResizeMarkers(
            canvas,
            boxWidth,
            boxHeight,
            toolStrokeWidth,
            rectangleShrinkingOnHighlight,
            linePaint
        )
    }

    protected fun setShapeSizeChangedListener(listener: ShapeSizeChangedListener) {
        this.shapeSizeChangedListener = listener
    }

    protected fun createAndSetShapeSizeText(width: Float, height: Float) {
        shapeSizeChangedListener?.onShapeSizeChanged("${width.toInt()} x ${height.toInt()} px")
    }

    protected fun toggleShapeSizeVisibility(isVisible: Boolean) {
        shapeSizeChangedListener?.onToggleVisibility(isVisible)
    }

    fun changeToolLayoutVisibility(toolOptionsView: View, willHide: Boolean, disabled: Boolean = false) {
        if (willHide) {
            if (toolOptionsView.visibility == View.VISIBLE) {
                toolOptionsViewController.slideDown(
                    toolOptionsView,
                    willHide = true,
                    showOptionsView = false,
                    setViewGone = disabled
                )
            }
        } else {
            val visibility = toolOptionsView.visibility
            if (visibility == View.GONE && disabled || visibility == View.INVISIBLE && !disabled) {
                toolOptionsViewController.slideUp(
                    toolOptionsView,
                    willHide = false,
                    showOptionsView = true
                )
            }
        }
    }

    interface ShapeSizeChangedListener {
        fun onShapeSizeChanged(shapeText: String)
        fun onToggleVisibility(isVisible: Boolean)
    }
}
