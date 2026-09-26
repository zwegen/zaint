package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.TwoFingerTransformTool
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.tools.options.ZaintFillOptions
import kotlin.math.atan2
import kotlin.math.hypot

const val DEFAULT_TOLERANCE_IN_PERCENT = 12
const val MAX_ABSOLUTE_TOLERANCE = 510

/**
 * Zaint's area fill interaction.
 *
 * The options panel only reports choices. This tool owns the palette and turns one
 * canvas tap into either a solid fill or Zaint's multi-color directional fill.
 */
class ZaintFillTool(
    private val options: ZaintFillOptions,
    private val openImagePicker: () -> Unit,
    contextCallback: ContextCallback,
    toolOptions: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintToolBase(
    contextCallback,
    toolOptions,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
), TwoFingerTransformTool {
    @VisibleForTesting
    @JvmField
    var colorTolerance = MAX_ABSOLUTE_TOLERANCE * DEFAULT_TOLERANCE_IN_PERCENT / PERCENT_SCALE

    private val palette = mutableListOf(toolPaint.paint.color)
    private var activePaletteIndex = 0
    private var direction = FillGradientDirection.TOP_BOTTOM
    private var imageSource: Bitmap? = null
    private var imagePreview: ImageFillPreview? = null
    private var fillAntialiasing = true
    private var colorPreview: ColorFillPreview? = null
    private var viewTransformMode = false
    private var previousTransformDistance = 0f
    private var previousTransformAngle = 0f
    private var previousTransformMidpoint = PointF()
    private var previewGestureStart: PointF? = null
    private var previewWasDragged = false
    private var tapAreaHintCenter: PointF? = null
    private val hintHandler = Handler(Looper.getMainLooper())
    private val hideTapAreaHint = Runnable {
        tapAreaHintCenter = null
        workspace.invalidate()
    }
    private val tapAreaHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tapAreaHintOutlinePaint = Paint(tapAreaHintPaint).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    init {
        options.renderFillColors(palette)
        options.setListener(object : ZaintFillOptions.Listener {
            override fun onToleranceSelected(percent: Int) {
                clearPendingFillPreviews()
                colorTolerance = percent.toAbsoluteTolerance()
            }

            override fun onGradientDirectionSelected(direction: FillGradientDirection) {
                clearPendingFillPreviews()
                this@ZaintFillTool.direction = direction
            }

            override fun onAddFillColor() {
                clearPendingFillPreviews()
                if (palette.size == MAX_PALETTE_SIZE) return

                palette += Color.WHITE
                activePaletteIndex = palette.lastIndex
                toolPaint.color = palette[activePaletteIndex]
                publishPalette()
            }

            override fun onFillColorSelected(index: Int) {
                clearPendingFillPreviews()
                if (index !in palette.indices) return

                activePaletteIndex = index
                toolPaint.color = palette[index]
                publishPalette()
            }

            override fun onRemoveFillColor(index: Int) {
                clearPendingFillPreviews()
                if (palette.size == MIN_PALETTE_SIZE || index !in palette.indices) return

                palette.removeAt(index)
                activePaletteIndex = activePaletteIndex.coerceAtMost(palette.lastIndex)
                toolPaint.color = palette[activePaletteIndex]
                publishPalette()
            }

            override fun onImageFillSelected() {
                if (imageSource == null) {
                    requestImageSource()
                } else {
                    options.renderImageFillSelected(true)
                }
            }

            override fun onImageFillDeselected() {
                imageSource = null
                clearPendingFillPreviews()
            }

            override fun onFillAntialiasingChanged(enabled: Boolean) {
                fillAntialiasing = enabled
                imagePreview?.setAntialiasing(enabled)
                colorPreview?.setAntialiasing(enabled)
                workspace.invalidate()
            }
        })
    }

    fun updateColorTolerance(colorToleranceInPercent: Int) {
        colorTolerance = colorToleranceInPercent.toAbsoluteTolerance()
    }

    fun getToleranceAbsoluteValue(toleranceInPercent: Int): Float =
        toleranceInPercent.toAbsoluteTolerance()

    override fun handleDown(coordinate: PointF?): Boolean {
        if (imagePreview == null || coordinate == null) return false
        if (viewTransformMode) {
            previewWasDragged = true
            return true
        }
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        previewGestureStart = PointF(coordinate.x, coordinate.y)
        previewWasDragged = false
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        val preview = imagePreview ?: return false
        if (viewTransformMode) return false
        val previous = previousEventCoordinate ?: return false
        coordinate ?: return false
        previewGestureStart?.let { start ->
            if (hypot(coordinate.x - start.x, coordinate.y - start.y) >= AREA_TAP_SLOP) {
                previewWasDragged = true
            }
        }
        preview.moveBy(coordinate.x - previous.x, coordinate.y - previous.y)
        previous.set(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        coordinate ?: return false
        if (!workspace.contains(coordinate)) return false

        if (imageSource != null) {
            if (imagePreview == null) {
                addImageFillRegion(coordinate, createsPreview = true) ?: return false
                hideTapAreaHint()
                toolOptionsViewController.showCheckmark()
                workspace.invalidate()
            } else if (!previewWasDragged) {
                toggleImageFillRegion(coordinate)
                workspace.invalidate()
            }
            previousEventCoordinate = null
            previewGestureStart = null
            previewWasDragged = false
            return true
        }

        if (colorPreview == null) {
            addColorFillRegion(coordinate, createsPreview = true) ?: return false
            toolOptionsViewController.showCheckmark()
        } else {
            toggleColorFillRegion(coordinate)
        }
        workspace.invalidate()
        return true
    }

    override fun changePaintColor(@ColorInt color: Int, invalidate: Boolean) {
        super.changePaintColor(color, invalidate)
        if (activePaletteIndex !in palette.indices) return

        palette[activePaletteIndex] = color
        publishPalette()
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun resetInternalState() {
        clearPendingFillPreviews()
    }

    override fun resetInternalState(stateChange: StateChange) {
        if (stateChange == StateChange.MOVE_CANCELED && (viewTransformMode || colorPreview != null)) return
        super.resetInternalState(stateChange)
    }

    override val toolType: ZaintToolKind = ZaintToolKind.FILL

    override fun handleUpAnimations(coordinate: PointF?) {
        super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    override fun draw(canvas: Canvas) {
        imagePreview?.draw(canvas)
        colorPreview?.draw(canvas, toolPaint.checkeredShader)
        tapAreaHintCenter?.let { center -> drawTapAreaHint(canvas, center) }
    }

    fun setBitmapFromSource(bitmap: Bitmap) {
        imageSource = bitmap
        clearPendingFillPreviews()
        options.renderImageFillSelected(true)
        showTapAreaHint()
        workspace.invalidate()
    }

    fun applyPendingFill(): Boolean {
        val image = imagePreview
        if (image != null) {
            commitPreview(image.bounds, image.bitmapForCommit())
            clearPendingFillPreviews()
            return true
        }
        val color = colorPreview ?: return false
        if (color.isTransparentSolidFill()) {
            commitMaskedClear(color.bounds, color.clearMaskForCommit())
        } else {
            commitPreview(color.bounds, color.bitmapForCommit())
        }
        clearPendingFillPreviews()
        return true
    }

    private fun commitPreview(bounds: android.graphics.Rect, bitmap: Bitmap) {
        commandManager.addCommand(
            commandFactory.createClipboardCommand(
                bitmap,
                PointF(bounds.exactCenterX(), bounds.exactCenterY()),
                bounds.width().toFloat(),
                bounds.height().toFloat(),
                0f
            )
        )
    }

    private fun commitMaskedClear(bounds: android.graphics.Rect, mask: Bitmap) {
        commandManager.addCommand(
            commandFactory.createMaskedClearCommand(
                mask,
                bounds
            )
        )
    }

    /** Kept for callers compiled against the previous image-only confirmation API. */
    fun applyImageFill(): Boolean = applyPendingFill()

    fun toggleViewTransformMode() {
        if (imagePreview == null) return
        viewTransformMode = !viewTransformMode
        toolOptionsViewController.showImageFillGestureToggle(viewTransformMode)
    }

    override fun handToolMode(): Boolean = imagePreview != null && viewTransformMode

    override fun beginTwoFingerTransform(first: PointF, second: PointF): Boolean {
        if (imagePreview == null || viewTransformMode) return false
        previousTransformDistance = distance(first, second)
        previousTransformAngle = angle(first, second)
        previousTransformMidpoint = midpoint(first, second)
        previousEventCoordinate = null
        previewGestureStart = null
        previewWasDragged = true
        return true
    }

    override fun updateTwoFingerTransform(first: PointF, second: PointF) {
        val preview = imagePreview ?: return
        if (viewTransformMode) return
        val distance = distance(first, second)
        val angle = angle(first, second)
        val midpoint = midpoint(first, second)
        if (previousTransformDistance > 0f && distance > 0f) {
            preview.transformBy(
                distance / previousTransformDistance,
                angle - previousTransformAngle,
                midpoint.x - previousTransformMidpoint.x,
                midpoint.y - previousTransformMidpoint.y
            )
            workspace.invalidate()
        }
        previousTransformDistance = distance
        previousTransformAngle = angle
        previousTransformMidpoint = midpoint
    }

    override fun endTwoFingerTransform() {
        previousTransformDistance = 0f
    }

    private fun publishPalette() {
        options.renderFillColors(palette)
    }

    private fun requestImageSource() {
        clearPendingFillPreviews()
        options.renderImageFillSelected(false)
        openImagePicker()
    }

    private fun clearPendingFillPreviews() {
        imagePreview?.release()
        imagePreview = null
        colorPreview?.release()
        colorPreview = null
        viewTransformMode = false
        previousEventCoordinate = null
        previousTransformDistance = 0f
        previewGestureStart = null
        previewWasDragged = false
        hideTapAreaHint()
        toolOptionsViewController.hideCheckmark()
        toolOptionsViewController.hideImageFillGestureToggle()
        workspace.invalidate()
    }

    private fun showTapAreaHint() {
        tapAreaHintCenter = workspace.getCanvasPointFromSurfacePoint(
            PointF(workspace.surfaceWidth / 2f, workspace.surfaceHeight / 2f)
        )
        hintHandler.removeCallbacks(hideTapAreaHint)
        hintHandler.postDelayed(hideTapAreaHint, TAP_AREA_HINT_DURATION_MS)
    }

    private fun hideTapAreaHint() {
        tapAreaHintCenter = null
        hintHandler.removeCallbacks(hideTapAreaHint)
    }

    private fun drawTapAreaHint(canvas: Canvas, center: PointF) {
        val scale = workspace.scale.coerceAtLeast(MIN_HINT_SCALE)
        tapAreaHintPaint.textSize = TAP_AREA_HINT_TEXT_SIZE_SP *
            contextCallback.displayMetrics.scaledDensity / scale
        tapAreaHintOutlinePaint.textSize = tapAreaHintPaint.textSize
        tapAreaHintOutlinePaint.strokeWidth = TAP_AREA_HINT_OUTLINE_WIDTH_DP *
            contextCallback.displayMetrics.density / scale
        val fontMetrics = tapAreaHintPaint.fontMetrics
        val baseline = center.y - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val text = contextCallback.context.getString(R.string.fill_tool_dialog_tap_area)
        canvas.drawText(text, center.x, baseline, tapAreaHintOutlinePaint)
        canvas.drawText(text, center.x, baseline, tapAreaHintPaint)
    }

    private fun addImageFillRegion(coordinate: PointF, createsPreview: Boolean): ImageFillPreview? {
        val preview = imagePreview
        val bitmap = workspace.bitmapOfCurrentLayer ?: return null
        val region = ImageFillRegionFinder.find(
            bitmap,
            coordinate.x.toInt(),
            coordinate.y.toInt(),
            colorTolerance
        ) ?: return null
        return if (createsPreview) {
            ImageFillPreview(imageSource ?: return null, region, coordinate, fillAntialiasing).also {
                imagePreview = it
                viewTransformMode = false
                toolOptionsViewController.showImageFillGestureToggle(false)
            }
        } else {
            preview?.addRegion(region)
            preview
        }
    }

    private fun toggleImageFillRegion(coordinate: PointF) {
        val preview = imagePreview ?: return
        if (preview.contains(coordinate)) {
            preview.removeRegionAt(coordinate)
            if (preview.isEmpty) {
                preview.release()
                imagePreview = null
                toolOptionsViewController.hideCheckmark()
                toolOptionsViewController.hideImageFillGestureToggle()
                showTapAreaHint()
            }
        } else {
            addImageFillRegion(coordinate, createsPreview = false)
        }
    }

    private fun addColorFillRegion(coordinate: PointF, createsPreview: Boolean): ColorFillPreview? {
        val preview = colorPreview
        val bitmap = workspace.bitmapOfCurrentLayer ?: return null
        val region = ImageFillRegionFinder.find(
            bitmap,
            coordinate.x.toInt(),
            coordinate.y.toInt(),
            colorTolerance
        ) ?: return null
        return if (createsPreview) {
            ColorFillPreview(region, palette.toIntArray(), direction, bitmap, fillAntialiasing).also { colorPreview = it }
        } else {
            preview?.addRegion(region)
            preview
        }
    }

    private fun toggleColorFillRegion(coordinate: PointF) {
        val preview = colorPreview ?: return
        if (preview.contains(coordinate)) {
            preview.removeRegionAt(coordinate)
            if (preview.isEmpty) {
                preview.release()
                colorPreview = null
                toolOptionsViewController.hideCheckmark()
            }
        } else {
            addColorFillRegion(coordinate, createsPreview = false)
        }
    }

    private fun distance(first: PointF, second: PointF): Float = hypot(second.x - first.x, second.y - first.y)

    private fun angle(first: PointF, second: PointF): Float = Math.toDegrees(
        atan2((second.y - first.y).toDouble(), (second.x - first.x).toDouble())
    ).toFloat()

    private fun midpoint(first: PointF, second: PointF): PointF = PointF(
        (first.x + second.x) / 2f,
        (first.y + second.y) / 2f
    )

    private fun Int.toAbsoluteTolerance(): Float =
        MAX_ABSOLUTE_TOLERANCE * this / PERCENT_SCALE

    private companion object {
        const val PERCENT_SCALE = 100f
        const val MIN_PALETTE_SIZE = 1
        const val GRADIENT_PALETTE_SIZE = 2
        const val MAX_PALETTE_SIZE = 3
        const val TAP_AREA_HINT_DURATION_MS = 1_000L
        const val TAP_AREA_HINT_TEXT_SIZE_SP = 20f
        const val TAP_AREA_HINT_OUTLINE_WIDTH_DP = 2f
        const val MIN_HINT_SCALE = 0.1f
        const val AREA_TAP_SLOP = 6f
    }
}
