package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.os.Bundle
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.clipboard.ClipboardPlacement
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.tools.ZaintSpeechBubbleOptionsPanel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val DEFAULT_BUBBLE_WIDTH = 300f
private const val DEFAULT_BUBBLE_HEIGHT = 190f
private const val DEFAULT_STROKE_WIDTH = 4
private const val TAIL_HANDLE_RADIUS = 12f
private const val BUBBLE_BITMAP_PADDING = 12f
private const val THOUGHT_CIRCLE_LARGE = 18f
private const val THOUGHT_CIRCLE_MEDIUM = 13f
private const val THOUGHT_CIRCLE_SMALL = 9f
private const val BUNDLE_TYPE = "SPEECH_BUBBLE_TYPE"
private const val BUNDLE_TAIL_X = "SPEECH_BUBBLE_TAIL_X"
private const val BUNDLE_TAIL_Y = "SPEECH_BUBBLE_TAIL_Y"
private const val BUNDLE_STROKE_WIDTH = "SPEECH_BUBBLE_STROKE_WIDTH"

/** A movable, resizable and rotatable speech-bubble preview committed as one undoable stamp. */
class SpeechBubbleTool(
    private val options: ZaintSpeechBubbleOptionsPanel,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline
) : ZaintRectangleToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {
    override val keepAspectRatioOnCornerResize = false

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.SPEECH_BUBBLE
    override var drawTime: Long = 0L

    private var type: SpeechBubbleType? = null
    private var tailPoint = PointF(0f, 0f)
    private var strokeWidth = DEFAULT_STROKE_WIDTH
    private var tailDragActive = false
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val previewHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val previewHandleOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    init {
        rotationEnabled = true
        shouldDrawRectangle = false
        options.setCallback(object : ZaintSpeechBubbleOptionsPanel.Callback {
            override fun onTypeSelected(type: SpeechBubbleType) = activate(type)
            override fun onStrokeWidthChanged(width: Int) {
                strokeWidth = width
                workspace.invalidate()
            }
        })
        options.setStrokeWidth(strokeWidth)
        activate(SpeechBubbleType.OVAL)
        options.showSelectedType(SpeechBubbleType.OVAL)
        toolOptionsViewController.showDelayed()
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun handleDown(coordinate: PointF?): Boolean {
        if (type != null && coordinate != null && isTailHandleHit(coordinate)) {
            tailDragActive = true
            previousEventCoordinate = PointF(coordinate.x, coordinate.y)
            return true
        }
        return super.handleDown(coordinate)
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        if (!tailDragActive || coordinate == null) return super.handleMove(coordinate, shouldAnimate)
        tailPoint = toLocal(coordinate)
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (!tailDragActive) return super.handleUp(coordinate)
        coordinate?.let { tailPoint = toLocal(it) }
        tailDragActive = false
        workspace.invalidate()
        return true
    }

    override fun drawBitmap(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        type?.let { drawBubble(canvas, it, boxWidth, boxHeight, tailPoint, strokeWidth.toFloat()) }
    }

    override fun drawToolSpecifics(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        if (type == null) return
        super.drawToolSpecifics(canvas, boxWidth, boxHeight)
        val handleRadius = getInverselyProportionalSizeForZoom(TAIL_HANDLE_RADIUS)
        previewHandlePaint.color = Color.WHITE
        previewHandleOutline.strokeWidth = getStrokeWidthForZoom(2f, 1f, 4f)
        canvas.drawCircle(tailPoint.x, tailPoint.y, handleRadius, previewHandlePaint)
        canvas.drawCircle(tailPoint.x, tailPoint.y, handleRadius, previewHandleOutline)
    }

    override fun onClickOnButton() = insertIntoLayer()

    private fun insertIntoLayer() {
        val currentType = type ?: return
        if (!boxIntersectsWorkspace()) return
        val bitmap = createBubbleBitmap(currentType)
        val command = commandFactory.createClipboardCommand(
            bitmap,
            PointF(toolPosition.x, toolPosition.y),
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            boxRotation
        )
        commandManager.addCommand(
            if (AutomaticLayerInsertion.shouldCreateNewLayer(workspace.layerModel.layerCount) {
                    overlapsVisibleCurrentLayerPixels(
                        VisiblePixelOverlap.transformedBounds(
                            toolPosition,
                            bitmap.width.toFloat(),
                            bitmap.height.toFloat(),
                            boxRotation
                        )
                    ) { canvas ->
                        ClipboardPlacement(
                            Point(toolPosition.x.toInt(), toolPosition.y.toInt()),
                            bitmap.width.toFloat(),
                            bitmap.height.toFloat(),
                            boxRotation
                        ).drawOn(canvas, bitmap)
                    }
                }
            ) {
                ZaintCommandBatch().apply {
                    addCommand(commandFactory.createAddEmptyLayerCommand())
                    addCommand(command)
                }
            } else command
        )
        highlightBox()
    }

    override fun resetInternalState() {
        type = null
        options.showSelectedType(null)
        workspace.invalidate()
    }

    override fun resetInternalState(stateChange: Tool.StateChange) {
        if (SpeechBubbleStatePolicy.preservesPreview(stateChange)) {
            // A pinch only interrupts the current drag. Keep the editable bubble.
            tailDragActive = false
            return
        }
        super.resetInternalState(stateChange)
    }

    override fun onSaveInstanceState(bundle: Bundle?) {
        super.onSaveInstanceState(bundle)
        bundle?.apply {
            putString(BUNDLE_TYPE, type?.name)
            putFloat(BUNDLE_TAIL_X, tailPoint.x)
            putFloat(BUNDLE_TAIL_Y, tailPoint.y)
            putInt(BUNDLE_STROKE_WIDTH, strokeWidth)
        }
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        bundle ?: return
        type = bundle.getString(BUNDLE_TYPE)?.let { savedType ->
            if (savedType == "EXPLOSION") SpeechBubbleType.CLOUD
            else runCatching { SpeechBubbleType.valueOf(savedType) }.getOrNull()
        }
        tailPoint.set(bundle.getFloat(BUNDLE_TAIL_X, tailPoint.x), bundle.getFloat(BUNDLE_TAIL_Y, tailPoint.y))
        strokeWidth = bundle.getInt(BUNDLE_STROKE_WIDTH, strokeWidth)
        options.setStrokeWidth(strokeWidth)
        options.showSelectedType(type)
    }

    private fun activate(selectedType: SpeechBubbleType) {
        type = selectedType
        boxRotation = 0f
        boxWidth = min(DEFAULT_BUBBLE_WIDTH, workspace.width * 0.8f)
        boxHeight = min(DEFAULT_BUBBLE_HEIGHT, workspace.height * 0.55f)
        toolPosition.set(workspace.width / 2f, workspace.height / 2f)
        tailPoint = defaultTailFor(boxWidth, boxHeight)
        workspace.invalidate()
    }

    private fun defaultTailFor(width: Float, height: Float): PointF = PointF(width * 0.28f, height * 0.84f)

    private fun isTailHandleHit(point: PointF): Boolean {
        val local = toLocal(point)
        return hypot((local.x - tailPoint.x).toDouble(), (local.y - tailPoint.y).toDouble()) <=
            getInverselyProportionalSizeForZoom(TAIL_HANDLE_RADIUS * 1.5f)
    }

    private fun toLocal(point: PointF): PointF {
        val dx = point.x - toolPosition.x
        val dy = point.y - toolPosition.y
        val angle = Math.toRadians(boxRotation.toDouble())
        return PointF(
            (dx * cos(angle) + dy * sin(angle)).toFloat(),
            (-dx * sin(angle) + dy * cos(angle)).toFloat()
        )
    }

    private fun createBubbleBitmap(currentType: SpeechBubbleType): Bitmap {
        val halfWidth = max(boxWidth / 2f, abs(tailPoint.x) + THOUGHT_CIRCLE_LARGE) + BUBBLE_BITMAP_PADDING + strokeWidth
        val halfHeight = max(boxHeight / 2f, abs(tailPoint.y) + THOUGHT_CIRCLE_LARGE) + BUBBLE_BITMAP_PADDING + strokeWidth
        val width = ceil(halfWidth * 2).toInt().coerceAtLeast(1)
        val height = ceil(halfHeight * 2).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                translate(width / 2f, height / 2f)
                drawBubble(this, currentType, boxWidth, boxHeight, tailPoint, strokeWidth.toFloat())
            }
        }
    }

    private fun drawBubble(
        canvas: Canvas,
        currentType: SpeechBubbleType,
        width: Float,
        height: Float,
        tail: PointF,
        lineWidth: Float
    ) {
        outlinePaint.strokeWidth = lineWidth
        when (currentType) {
            SpeechBubbleType.OVAL,
            SpeechBubbleType.ROUNDED_RECTANGLE,
            SpeechBubbleType.CLOUD -> drawBubbleWithTail(canvas, currentType, width, height, tail)
            SpeechBubbleType.THOUGHT -> drawThoughtBubble(canvas, width, height, tail)
        }
    }

    private fun drawBubbleWithTail(
        canvas: Canvas,
        currentType: SpeechBubbleType,
        width: Float,
        height: Float,
        tail: PointF
    ) {
        val joinedPath = SpeechBubbleGeometry.bodyPath(currentType, width, height).apply {
            op(SpeechBubbleGeometry.tailPath(currentType, width, height, tail, outlinePaint.strokeWidth), Path.Op.UNION)
        }
        canvas.drawPath(joinedPath, bubblePaint)
        canvas.drawPath(joinedPath, outlinePaint)
    }

    private fun drawThoughtBubble(canvas: Canvas, width: Float, height: Float, tail: PointF) {
        val bodyPath = SpeechBubbleGeometry.bodyPath(SpeechBubbleType.THOUGHT, width, height)
        canvas.drawPath(bodyPath, bubblePaint)
        canvas.drawPath(bodyPath, outlinePaint)
        val attachment = SpeechBubbleGeometry.tailAttachmentPoint(SpeechBubbleType.THOUGHT, width, height, tail)
        val dx = tail.x - attachment.x
        val dy = tail.y - attachment.y
        val circles = floatArrayOf(THOUGHT_CIRCLE_LARGE, THOUGHT_CIRCLE_MEDIUM, THOUGHT_CIRCLE_SMALL)
        circles.forEachIndexed { index, radius ->
            val fraction = (index + 1) / 4f
            val centerX = attachment.x + dx * fraction
            val centerY = attachment.y + dy * fraction
            canvas.drawCircle(centerX, centerY, radius, bubblePaint)
            canvas.drawCircle(centerX, centerY, radius, outlinePaint)
        }
    }

}
