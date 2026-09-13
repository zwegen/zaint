package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.serialization.ZaintTextStyle
import de.zwegen.zpaint.common.ITALIC_FONT_BOX_ADJUSTMENT
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ZaintFontFamily
import de.zwegen.zpaint.tools.GoogleFontRepository
import de.zwegen.zpaint.tools.TextFont
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintTextOptions
import de.zwegen.zpaint.tools.options.ZaintTextEffect
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.Exception
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@VisibleForTesting
const val TEXT_SIZE_MAGNIFICATION_FACTOR = 3f

@VisibleForTesting
const val BOX_OFFSET = 16

@VisibleForTesting
const val MARGIN_TOP = 200f

private const val ROTATION_ENABLED = true
private const val RESIZE_POINTS_VISIBLE = true
private const val ITALIC_TEXT_SKEW = -0.25f
private const val DEFAULT_TEXT_SKEW = 0.0f
private const val DEFAULT_TEXT_SIZE = 26
private const val DEFAULT_LINE_SPACING_PERCENT = 100
private const val LINE_SPACING_MIN_PERCENT = 60
private const val LINE_SPACING_MAX_PERCENT = 140
private const val DEFAULT_LETTER_SPACING_PERCENT = 0
private const val LETTER_SPACING_MIN_PERCENT = -40
private const val LETTER_SPACING_MAX_PERCENT = 40
private const val ANGLE_SNAP_DISTANCE_DEGREES = 4f
private const val ANGLE_SNAP_RELEASE_DISTANCE_DEGREES = 8f
private const val CURVATURE_SNAP_DISTANCE_DEGREES = 4f
private const val CURVATURE_SNAP_RELEASE_DISTANCE_DEGREES = 8f
private const val TEXT_OUTLINE_WIDTH_RATIO = 0.08f
private const val TEXT_SHADOW_BLUR_RATIO = 0.12f
private const val TEXT_SHADOW_ALPHA = 150
private const val CONTRAST_BACKGROUND_LUMINANCE_THRESHOLD = 0.55f
private const val TEXT_BACKGROUND_PADDING_RATIO = 0.18f
private const val LAST_LINE_JUSTIFY_THRESHOLD = 0.78f
private const val MAX_TEXT_CURVATURE_DEGREES = 180f
private const val CURVE_GRIP_DISTANCE = 30f
private const val CURVE_GRIP_RADIUS = 12f
private const val CURVE_DRAG_DISTANCE = 180f
private const val BUNDLE_TOOL_UNDERLINED = "BUNDLE_TOOL_UNDERLINED"
private const val BUNDLE_TOOL_ITALIC = "BUNDLE_TOOL_ITALIC"
private const val BUNDLE_TOOL_BOLD = "BUNDLE_TOOL_BOLD"
private const val BUNDLE_TOOL_OUTLINE = "BUNDLE_TOOL_OUTLINE"
private const val BUNDLE_TOOL_SHADOW = "BUNDLE_TOOL_SHADOW"
private const val BUNDLE_TOOL_TEXT_BACKGROUND = "BUNDLE_TOOL_TEXT_BACKGROUND"
private const val BUNDLE_TOOL_TEXT = "BUNDLE_TOOL_TEXT"
private const val BUNDLE_TOOL_TEXT_HAS_BEEN_EDITED = "BUNDLE_TOOL_TEXT_HAS_BEEN_EDITED"
private const val BUNDLE_TOOL_TEXT_SIZE = "BUNDLE_TOOL_TEXT_SIZE"
private const val BUNDLE_TOOL_LINE_SPACING_PERCENT = "BUNDLE_TOOL_LINE_SPACING_PERCENT"
private const val BUNDLE_TOOL_LETTER_SPACING_PERCENT = "BUNDLE_TOOL_LETTER_SPACING_PERCENT"
private const val BUNDLE_TOOL_FONT = "BUNDLE_TOOL_FONT"
private const val BUNDLE_TOOL_CUSTOM_FONT_NAME = "BUNDLE_TOOL_CUSTOM_FONT_NAME"
private const val BUNDLE_TOOL_CUSTOM_FONT_URI = "BUNDLE_TOOL_CUSTOM_FONT_URI"
private const val BUNDLE_TOOL_TEXT_ALIGNMENT = "BUNDLE_TOOL_TEXT_ALIGNMENT"
private const val BUNDLE_TOOL_TEXT_JUSTIFIED = "BUNDLE_TOOL_TEXT_JUSTIFIED"
private const val BUNDLE_TOOL_USER_DEFINED_TEXT_BOX = "BUNDLE_TOOL_USER_DEFINED_TEXT_BOX"
private const val BUNDLE_TOOL_USER_DEFINED_TEXT_BOX_HEIGHT = "BUNDLE_TOOL_USER_DEFINED_TEXT_BOX_HEIGHT"
private const val BUNDLE_TOOL_TEXT_CURVATURE_DEGREES = "BUNDLE_TOOL_TEXT_CURVATURE_DEGREES"
private const val TAG = "Can't set custom font"

class ZaintTextTool(
    private val textToolOptionsView: ZaintTextOptions,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintRectangleToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
), ZaintRectangleToolBase.ShapeSizeChangedListener {
    @VisibleForTesting
    @JvmField
    val textPaint: Paint

    @VisibleForTesting
    @JvmField
    var text = contextCallback.context.getString(R.string.text_tool_dialog_placeholder)

    private var isInitialText = true

    @VisibleForTesting
    @JvmField
    var font = TextFont.builtIn(ZaintFontFamily.INTER)

    @VisibleForTesting
    @JvmField
    var underlined = false

    @VisibleForTesting
    @JvmField
    var italic = false

    @VisibleForTesting
    @JvmField
    var bold = false

    @VisibleForTesting
    @JvmField
    var outline = false

    @VisibleForTesting
    @JvmField
    var shadow = false

    @VisibleForTesting
    @JvmField
    var textBackground = false

    @VisibleForTesting
    @JvmField
    var textAlignment = Paint.Align.LEFT

    @VisibleForTesting
    @JvmField
    var justified = false

    @VisibleForTesting
    @JvmField
    var textCurvatureDegrees = 0f

    private var textSize = DEFAULT_TEXT_SIZE
    private var lineSpacingPercent = DEFAULT_LINE_SPACING_PERCENT
    private var letterSpacingPercent = DEFAULT_LETTER_SPACING_PERCENT
    private var showVerticalCenterGuide = false
    private var showHorizontalCenterGuide = false
    private var isTextBoxBeingDragged = false
    private val centerGuideSnap = CanvasCenterGuideSnap()
    private val angleSnap = FloatingBoxAngleSnap(
        ANGLE_SNAP_DISTANCE_DEGREES,
        ANGLE_SNAP_RELEASE_DISTANCE_DEGREES
    )
    private var lockedSnapAngle: Float? = null
    private var showAngleSnapGuide = false
    private var rawBoxRotation = 0f
    private var textBoxTapCandidate = false
    private var userDefinedTextBox = false
    private var userDefinedTextBoxHeight = false
    private var oldBoxWidth = 0f
    private var oldBoxHeight = 0f
    private var oldToolPosition: PointF? = null
    private var isCurvatureGripBeingDragged = false
    private var curvatureDragStartY = 0f
    private var curvatureDragStartDegrees = 0f
    private val curvatureSnap = ZeroValueSnap(
        CURVATURE_SNAP_DISTANCE_DEGREES,
        CURVATURE_SNAP_RELEASE_DISTANCE_DEGREES
    )
    private var curvatureSnapLocked = false

    @get:VisibleForTesting
    val multilineText: Array<String>
        get() = text.split("\n").toTypedArray()

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.TEXT

    override fun handleUpAnimations(coordinate: PointF?) {
        showTextToolLayout()
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    init {
        rotationEnabled = ROTATION_ENABLED
        resizePointsVisible = RESIZE_POINTS_VISIBLE
        setShapeSizeChangedListener(this)
        textPaint = Paint()
        initializePaint()
        resetPreview()
        resetBoxPosition()

        val callback: ZaintTextOptions.Callback = object : ZaintTextOptions.Callback {
            override fun setText(text: String) {
                this@ZaintTextTool.text = text
                isInitialText = false
                updateActiveLetterSpacing()
                if (!canCurveText()) {
                    textCurvatureDegrees = 0f
                }
                // The text itself defines the needed space, even after the box was resized manually.
                userDefinedTextBox = false
                userDefinedTextBoxHeight = false
                resetPreview()
                workspace.invalidate()
            }

            override fun setFont(textFont: TextFont) {
                if (textFont.id == font.id) return
                this@ZaintTextTool.font = GoogleFontRepository.enrichFont(contextCallback.context, textFont)
                normalizeFontStyle()
                updateTypeface()
                resetPreviewForFontChange()
                workspace.invalidate()
            }

            override fun setUnderlined(underlined: Boolean) {
                this@ZaintTextTool.underlined = underlined
                textPaint.isUnderlineText = this@ZaintTextTool.underlined
                storeAttributes()
                resetPreview()
                workspace.invalidate()
                applyAttributes()
            }

            override fun setItalic(italic: Boolean) {
                if (!font.supportsStyle(bold, italic)) return
                this@ZaintTextTool.italic = italic
                storeAttributes(italic)
                updateTypeface()
                resetPreview()
                workspace.invalidate()
                applyAttributes(italic)
            }

            override fun setBold(bold: Boolean) {
                if (!font.supportsStyle(bold, italic)) return
                this@ZaintTextTool.bold = bold
                storeAttributes()
                updateTypeface()
                resetPreview()
                workspace.invalidate()
                applyAttributes()
            }

            override fun setLineSpacingPercent(lineSpacingPercent: Int) {
                val previousNaturalBoxHeight = calculateNaturalBoxHeight(this@ZaintTextTool.lineSpacingPercent)
                this@ZaintTextTool.lineSpacingPercent =
                    lineSpacingPercent.coerceIn(LINE_SPACING_MIN_PERCENT, LINE_SPACING_MAX_PERCENT)
                if (userDefinedTextBox && userDefinedTextBoxHeight) {
                    resizeManualBoxHeightForLineSpacing(previousNaturalBoxHeight)
                } else {
                    resetPreview()
                }
                workspace.invalidate()
            }

            override fun setLetterSpacingPercent(letterSpacingPercent: Int) {
                val previousTextWidth = maxTextWidthForPreview()
                val previousContentHeight = getTextHeight(
                    textPaint.ascent(),
                    textPaint.descent(),
                    multilineText.size
                ) + curveSagittaForPreview()
                this@ZaintTextTool.letterSpacingPercent =
                    letterSpacingPercent.coerceIn(LETTER_SPACING_MIN_PERCENT, LETTER_SPACING_MAX_PERCENT)
                updateActiveLetterSpacing()
                if (userDefinedTextBox) {
                    resizeManualBoxForLetterSpacing(previousTextWidth, previousContentHeight)
                } else {
                    resetPreview()
                }
                workspace.invalidate()
            }

            override fun setTextAlignment(textAlignment: Paint.Align, justified: Boolean) {
                val wasJustified = this@ZaintTextTool.justified
                this@ZaintTextTool.textAlignment = textAlignment
                this@ZaintTextTool.justified = justified
                textPaint.textAlign = this@ZaintTextTool.textAlignment
                if (justified && !wasJustified) {
                    centerJustifiedTextBoxIfNarrowerThanCanvas()
                }
                workspace.invalidate()
            }

            override fun setTextEffect(textEffect: ZaintTextEffect) {
                outline = textEffect == ZaintTextEffect.OUTLINE
                shadow = textEffect == ZaintTextEffect.SHADOW
                textBackground = textEffect == ZaintTextEffect.BACKGROUND
                workspace.invalidate()
            }

            override fun setTextSize(size: Int) {
                textSize = size
                textPaint.textSize = textSize * TEXT_SIZE_MAGNIFICATION_FACTOR
                resetPreview()
                workspace.invalidate()
            }

            override fun hideToolOptions() {
                this@ZaintTextTool.toolOptionsViewController.hide()
            }
        }
        textToolOptionsView.setCallback(callback)
        textToolOptionsView.setState(
            bold,
            italic,
            underlined,
            text,
            textSize,
            font,
            lineSpacingPercent,
            letterSpacingPercent,
            textAlignment,
            justified,
            outline,
            shadow,
            textBackground
        )
        toolOptionsViewController.showDelayed()
    }

    private fun initializePaint() {
        textPaint.isAntiAlias = DEFAULT_ANTIALIASING_ON
        textPaint.color = toolPaint.previewColor
        textPaint.textSize = DEFAULT_TEXT_SIZE * TEXT_SIZE_MAGNIFICATION_FACTOR
        textPaint.isUnderlineText = underlined
        textPaint.isFakeBoldText = false
        textPaint.textAlign = textAlignment
        updateActiveLetterSpacing()
        updateTypeface()
    }

    fun hideTextToolLayout() {
        if (textToolOptionsView.getTopLayout().visibility == View.VISIBLE) {
            toolOptionsViewController.slideUp(
                textToolOptionsView.getTopLayout(),
                willHide = true,
                showOptionsView = false
            )
        }

        if (textToolOptionsView.getBottomLayout().visibility == View.VISIBLE) {
            toolOptionsViewController.slideDown(
                textToolOptionsView.getBottomLayout(),
                willHide = true,
                showOptionsView = false
            )
        }
    }

     fun showTextToolLayout() {
         if (textToolOptionsView.getTopLayout().visibility == View.INVISIBLE) {
             if (!toolOptionsViewController.isVisible) {
                 toolOptionsViewController.show()
             }
             toolOptionsViewController.slideDown(
                 textToolOptionsView.getTopLayout(),
                 willHide = false,
                 showOptionsView = true
             )
         }

         if (textToolOptionsView.getBottomLayout().visibility == View.INVISIBLE) {
             if (!toolOptionsViewController.isVisible) {
                 toolOptionsViewController.show()
             }
             toolOptionsViewController.slideUp(
                 textToolOptionsView.getBottomLayout(),
                 willHide = false,
                 showOptionsView = true
             )
         }
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        if (isCurvatureGripBeingDragged) {
            coordinate?.let(::updateTextCurvatureFromGrip)
            return true
        }
        textToolOptionsView.hideKeyboard()
        val previousBoxWidth = boxWidth
        val previousBoxHeight = boxHeight
        val handled = super.handleMove(coordinate, false)
        if (boxWidth != previousBoxWidth || boxHeight != previousBoxHeight) {
            userDefinedTextBox = true
        }
        if (boxHeight != previousBoxHeight) {
            userDefinedTextBoxHeight = true
        }
        if (isTextBoxBeingDragged) {
            snapTextBoxToImageCenter(coordinate)
            workspace.invalidate()
        }
        return handled
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        resetCenterSnapLocks()
        if (coordinate != null && canCurveText() && isOnCurvatureGrip(coordinate)) {
            isCurvatureGripBeingDragged = true
            curvatureDragStartY = localPointFor(coordinate).y
            curvatureDragStartDegrees = textCurvatureDegrees
            curvatureSnapLocked = false
            textBoxTapCandidate = false
            return true
        }
        val handled = super.handleDown(coordinate)
        lockedSnapAngle = null
        showAngleSnapGuide = false
        rawBoxRotation = boxRotation
        isTextBoxBeingDragged = isMovingFloatingBox()
        textBoxTapCandidate = isTextBoxBeingDragged && coordinate?.let { boxContainsPoint(it) } == true
        return handled
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (isCurvatureGripBeingDragged) {
            isCurvatureGripBeingDragged = false
            curvatureSnapLocked = false
            textToolOptionsView.hideKeyboard()
            showTextToolLayout()
            return true
        }
        isTextBoxBeingDragged = false
        showVerticalCenterGuide = false
        showHorizontalCenterGuide = false
        lockedSnapAngle = null
        showAngleSnapGuide = false
        resetCenterSnapLocks()
        textToolOptionsView.hideKeyboard()
        super.handleUp(coordinate)
        if (textBoxTapCandidate && movedDistance.x <= contextCallback.scrollTolerance && movedDistance.y <= contextCallback.scrollTolerance) {
            textToolOptionsView.showTextInputDialog(text, isInitialText)
        }
        textBoxTapCandidate = false
        return true
    }

    override fun drawToolSpecifics(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        drawCenterGuides(canvas)
        drawAngleSnapGuide(canvas)
        drawCurvatureSnapGuide(canvas)
        super.drawToolSpecifics(canvas, boxWidth, boxHeight)
        drawCurvatureGrip(canvas, boxHeight)
    }

    private fun canCurveText(): Boolean = multilineText.size == 1 && text.isNotEmpty()

    private fun localPointFor(point: PointF): PointF {
        val radians = Math.toRadians((-boxRotation).toDouble())
        val deltaX = point.x - toolPosition.x
        val deltaY = point.y - toolPosition.y
        return PointF(
            (deltaX * cos(radians) - deltaY * sin(radians)).toFloat(),
            (deltaX * sin(radians) + deltaY * cos(radians)).toFloat()
        )
    }

    private fun curvatureGripY(): Float =
        -boxHeight / 2f - getInverselyProportionalSizeForZoom(CURVE_GRIP_DISTANCE)

    private fun isOnCurvatureGrip(point: PointF): Boolean {
        val local = localPointFor(point)
        val hitRadius = getInverselyProportionalSizeForZoom(CURVE_GRIP_RADIUS * 1.5f)
        return local.x * local.x + (local.y - curvatureGripY()) * (local.y - curvatureGripY()) <=
            hitRadius * hitRadius
    }

    private fun updateTextCurvatureFromGrip(point: PointF) {
        val previousCurveSagitta = curveSagittaForPreview()
        val localY = localPointFor(point).y
        val dragDistance = getInverselyProportionalSizeForZoom(CURVE_DRAG_DISTANCE)
        val rawCurvature = (curvatureDragStartDegrees +
            (curvatureDragStartY - localY) * MAX_TEXT_CURVATURE_DEGREES / dragDistance)
            .coerceIn(-MAX_TEXT_CURVATURE_DEGREES, MAX_TEXT_CURVATURE_DEGREES)
        curvatureSnapLocked = curvatureSnap.resolve(rawCurvature, curvatureSnapLocked)
        textCurvatureDegrees = if (curvatureSnapLocked) 0f else rawCurvature
        resizeBoxForTextCurvature(previousCurveSagitta)
        workspace.invalidate()
    }

    private fun drawCurvatureSnapGuide(canvas: Canvas) {
        if (!curvatureSnapLocked) return

        val length = maxOf(workspace.width, workspace.height).toFloat()
        drawCenterGuideLine(canvas, -length, 0f, length, 0f)
    }

    private fun drawCurvatureGrip(canvas: Canvas, boxHeight: Float) {
        if (!canCurveText()) return
        val radius = getInverselyProportionalSizeForZoom(CURVE_GRIP_RADIUS)
        val y = -boxHeight / 2f - getInverselyProportionalSizeForZoom(CURVE_GRIP_DISTANCE)
        linePaint.apply {
            color = primaryShapeColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(0f, y, radius, linePaint)
        linePaint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = toolStrokeWidth
        }
        canvas.drawCircle(0f, y, radius, linePaint)
        canvas.drawLine(0f, -boxHeight / 2f, 0f, y + radius, linePaint)
        linePaint.style = Paint.Style.STROKE
    }

    private fun snapTextBoxToImageCenter(coordinate: PointF?) {
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
    }

    private fun resetCenterSnapLocks() {
        centerGuideSnap.reset()
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
        drawCenterGuideLine(canvas, start.x, start.y, stop.x, stop.y)
    }

    override fun rotationReferenceForDrag(): Float = rawBoxRotation

    override fun resolveBoxRotationAfterDrag(rawRotation: Float): Float {
        rawBoxRotation = rawRotation
        lockedSnapAngle = angleSnap.resolve(rawRotation, lockedSnapAngle)
        showAngleSnapGuide = lockedSnapAngle != null
        return lockedSnapAngle ?: rawRotation
    }

    private fun drawCenterGuides(canvas: Canvas) {
        CanvasCenterGuideRenderer.draw(
            canvas,
            workspace.width.toFloat(),
            workspace.height.toFloat(),
            toolPosition.x,
            toolPosition.y,
            boxRotation,
            currentCanvasScale(),
            linePaint,
            showVerticalCenterGuide,
            showHorizontalCenterGuide
        )
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

    private fun drawCenterGuideLine(canvas: Canvas, startX: Float, startY: Float, stopX: Float, stopY: Float) {
        CanvasCenterGuideRenderer.drawGuideLine(
            canvas,
            startX,
            startY,
            stopX,
            stopY,
            currentCanvasScale(),
            linePaint
        )
    }

    private fun currentCanvasScale(): Float =
        workspace.scale.coerceAtLeast(0.01f)

    override fun drawBitmap(canvas: Canvas, boxWidth: Float, boxHeight: Float) {
        val textAscent = textPaint.ascent()
        val textDescent = textPaint.descent()
        val lineHeight = getLineHeight(textAscent, textDescent)
        val textHeight = getTextHeight(textAscent, textDescent, multilineText.size)
        var maxTextWidth = multilineText.maxOf { line ->
            textPaint.measureText(line)
        }.coerceAtLeast(1f)

        if (italic) {
            maxTextWidth *= ITALIC_FONT_BOX_ADJUSTMENT
        }

        canvas.save()

        val widthScaling = (boxWidth - 2 * BOX_OFFSET) / maxTextWidth
        val heightScaling = (boxHeight - 2 * BOX_OFFSET) /
            (textHeight + curveSagittaForPreview())

        canvas.scale(widthScaling, heightScaling)

        val scaledHeightOffset = BOX_OFFSET / heightScaling
        val scaledWidthOffset = BOX_OFFSET / widthScaling
        val scaledBoxWidth = boxWidth / widthScaling
        val scaledBoxHeight = boxHeight / heightScaling

        if (textBackground) {
            drawTextBackground(canvas, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, maxTextWidth, textAscent, textDescent, lineHeight)
        }

        multilineText.forEachIndexed { index, textLine ->
            if (shadow) {
                drawTextLine(canvas, textLine, index, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, textAscent, lineHeight, maxTextWidth, createShadowPaint())
            }
            if (outline) {
                drawTextLine(canvas, textLine, index, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, textAscent, lineHeight, maxTextWidth, createOutlinePaint())
            }
            drawTextLine(canvas, textLine, index, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, textAscent, lineHeight, maxTextWidth, textPaint)
        }
        canvas.restore()
    }

    private fun drawTextLine(
        canvas: Canvas,
        textLine: String,
        index: Int,
        scaledWidthOffset: Float,
        scaledHeightOffset: Float,
        scaledBoxWidth: Float,
        scaledBoxHeight: Float,
        textAscent: Float,
        lineHeight: Float,
        justifiedWidth: Float,
        paint: Paint
    ) {
        if (canCurveText() && textCurvatureDegrees != 0f) {
            val textWidth = paint.measureText(textLine)
            val textLeft = getAlignedTextLeft(scaledWidthOffset, scaledBoxWidth, textWidth, italic)
            val sagitta = TextCurveRenderer.sagitta(textWidth, textCurvatureDegrees)
            // TextCurveRenderer positions glyphs by their visual centre, unlike Canvas.drawText(),
            // which uses a baseline. Convert the box's content top to that visual centre so a
            // curved line stays vertically centred in the same box as straight text.
            val arcEndpointCenterY = -(scaledBoxHeight / 2) + scaledHeightOffset +
                (paint.descent() - paint.ascent()) / 2f +
                if (textCurvatureDegrees < 0f) sagitta else 0f
            TextCurveRenderer.draw(
                canvas,
                textLine,
                paint,
                textLeft + textWidth / 2f,
                arcEndpointCenterY,
                textCurvatureDegrees
            )
            return
        }
        val textX = getAlignedTextX(scaledWidthOffset, scaledBoxWidth, italic)
        val baseline = -(scaledBoxHeight / 2) + scaledHeightOffset - textAscent + lineHeight * index
        if (justified && shouldJustifyLine(index, textLine, justifiedWidth, paint)) {
            drawJustifiedTextLine(canvas, textLine, textX, baseline, justifiedWidth, paint)
        } else {
            canvas.drawText(textLine, textX, baseline, paint)
        }
    }

    private fun shouldJustifyLine(index: Int, textLine: String, targetWidth: Float, paint: Paint): Boolean =
        index < multilineText.lastIndex || paint.measureText(textLine) >= targetWidth * LAST_LINE_JUSTIFY_THRESHOLD

    private fun drawJustifiedTextLine(
        canvas: Canvas,
        textLine: String,
        x: Float,
        baseline: Float,
        targetWidth: Float,
        paint: Paint
    ) {
        val words = textLine.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) {
            canvas.drawText(textLine, x, baseline, paint)
            return
        }
        val extraSpacing = (targetWidth - paint.measureText(textLine)) / (words.size - 1)
        if (extraSpacing <= 0f) {
            canvas.drawText(textLine, x, baseline, paint)
            return
        }
        var wordX = x
        words.forEachIndexed { wordIndex, word ->
            canvas.drawText(word, wordX, baseline, paint)
            wordX += paint.measureText(word)
            if (wordIndex < words.lastIndex) {
                wordX += paint.measureText(" ") + extraSpacing
            }
        }
    }

    private fun drawTextBackground(
        canvas: Canvas,
        scaledWidthOffset: Float,
        scaledHeightOffset: Float,
        scaledBoxWidth: Float,
        scaledBoxHeight: Float,
        maxTextWidth: Float,
        textAscent: Float,
        textDescent: Float,
        lineHeight: Float
    ) {
        val textLeft = getAlignedTextLeft(scaledWidthOffset, scaledBoxWidth, maxTextWidth, italic)
        val curveSagitta = curveSagittaForPreview()
        val firstBaseline = -(scaledBoxHeight / 2) + scaledHeightOffset - textAscent +
            if (textCurvatureDegrees < 0f) curveSagitta else 0f
        val padding = textPaint.textSize * TEXT_BACKGROUND_PADDING_RATIO
        val top = firstBaseline + textAscent - padding - if (textCurvatureDegrees < 0f) curveSagitta else 0f
        val bottom = firstBaseline + lineHeight * (multilineText.size - 1) + textDescent + padding +
            if (textCurvatureDegrees > 0f) curveSagitta else 0f
        canvas.drawRect(RectF(textLeft - padding, top, textLeft + maxTextWidth + padding, bottom), createTextBackgroundPaint())
    }

    private fun createTextBackgroundPaint(): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contrastBackgroundColorFor(textPaint.color)
            style = Paint.Style.FILL
        }

    private fun contrastBackgroundColorFor(textColor: Int): Int {
        val luminance = (
            0.2126f * Color.red(textColor) +
                0.7152f * Color.green(textColor) +
                0.0722f * Color.blue(textColor)
            ) / 255f
        return if (luminance < CONTRAST_BACKGROUND_LUMINANCE_THRESHOLD) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    private fun createOutlinePaint(): Paint =
        Paint(textPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = textPaint.textSize * TEXT_OUTLINE_WIDTH_RATIO
            color = Color.BLACK
            maskFilter = null
            clearShadowLayer()
        }

    private fun createShadowPaint(): Paint =
        Paint(textPaint).apply {
            color = Color.argb(TEXT_SHADOW_ALPHA, 0, 0, 0)
            maskFilter = BlurMaskFilter(textPaint.textSize * TEXT_SHADOW_BLUR_RATIO, BlurMaskFilter.Blur.NORMAL)
            clearShadowLayer()
        }

    private fun getAlignedTextX(
        scaledWidthOffset: Float,
        scaledBoxWidth: Float,
        italic: Boolean
    ): Float {
        val italicAdjustment = if (italic) ITALIC_FONT_BOX_ADJUSTMENT else 1f
        return when (textAlignment) {
            Paint.Align.CENTER -> 0f
            Paint.Align.RIGHT -> scaledBoxWidth / 2 / italicAdjustment - scaledWidthOffset
            else -> scaledWidthOffset - scaledBoxWidth / 2 / italicAdjustment
        }
    }

    private fun getAlignedTextLeft(
        scaledWidthOffset: Float,
        scaledBoxWidth: Float,
        maxTextWidth: Float,
        italic: Boolean
    ): Float {
        val textX = getAlignedTextX(scaledWidthOffset, scaledBoxWidth, italic)
        return when (textAlignment) {
            Paint.Align.CENTER -> textX - maxTextWidth / 2f
            Paint.Align.RIGHT -> textX - maxTextWidth
            else -> textX
        }
    }

    private fun resetPreview(respectUserDefinedHeight: Boolean = false) {
        val textDescent = textPaint.descent()
        val textAscent = textPaint.ascent()
        val textHeight = getTextHeight(textAscent, textDescent, multilineText.size)
        val naturalBoxHeight = textHeight + curveSagittaForPreview() + 2 * BOX_OFFSET

        val maxTextWidth = maxTextWidthForPreview()
        if (userDefinedTextBox) {
            if (!respectUserDefinedHeight || !userDefinedTextBoxHeight) {
                boxHeight = naturalBoxHeight
            }
            createAndSetShapeSizeText(boxWidth, boxHeight)
            return
        }
        boxHeight = naturalBoxHeight
        boxWidth = maxTextWidth + 2 * BOX_OFFSET
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    /** A new font gets its own natural box; readable text takes priority over a manual box shape. */
    private fun resetPreviewForFontChange() {
        userDefinedTextBox = false
        userDefinedTextBoxHeight = false
        resetPreview()
    }

    private fun resizeManualBoxHeightForLineSpacing(previousNaturalBoxHeight: Float) {
        val currentContentHeight = (boxHeight - 2 * BOX_OFFSET).coerceAtLeast(1f)
        val previousNaturalContentHeight = (previousNaturalBoxHeight - 2 * BOX_OFFSET).coerceAtLeast(1f)
        val newNaturalContentHeight = (calculateNaturalBoxHeight(lineSpacingPercent) - 2 * BOX_OFFSET)
            .coerceAtLeast(1f)
        boxHeight = currentContentHeight * newNaturalContentHeight / previousNaturalContentHeight +
            2 * BOX_OFFSET
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    /** Keeps a manually resized text box at the same scale when letter spacing changes. */
    private fun resizeManualBoxForLetterSpacing(previousTextWidth: Float, previousContentHeight: Float) {
        val currentContentWidth = (boxWidth - 2 * BOX_OFFSET).coerceAtLeast(1f)
        val currentContentHeight = (boxHeight - 2 * BOX_OFFSET).coerceAtLeast(1f)
        val newTextWidth = maxTextWidthForPreview()
        val newContentHeight = getTextHeight(
            textPaint.ascent(),
            textPaint.descent(),
            multilineText.size
        ) + curveSagittaForPreview()

        boxWidth = currentContentWidth * newTextWidth / previousTextWidth.coerceAtLeast(1f) +
            2 * BOX_OFFSET
        boxHeight = if (userDefinedTextBoxHeight) {
            currentContentHeight * newContentHeight / previousContentHeight.coerceAtLeast(1f) +
                2 * BOX_OFFSET
        } else {
            calculateNaturalBoxHeight(lineSpacingPercent)
        }
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    private fun maxTextWidthForPreview(): Float {
        val textWidth = multilineText.maxOf { line -> textPaint.measureText(line) }.coerceAtLeast(1f)
        return if (italic) textWidth * ITALIC_FONT_BOX_ADJUSTMENT else textWidth
    }

    private fun calculateNaturalBoxHeight(lineSpacingPercent: Int): Float {
        val textDescent = textPaint.descent()
        val textAscent = textPaint.ascent()
        return getTextHeight(textAscent, textDescent, multilineText.size, lineSpacingPercent) +
            curveSagittaForPreview() +
            2 * BOX_OFFSET
    }

    private fun curveSagittaForPreview(): Float =
        if (canCurveText()) TextCurveRenderer.sagitta(textPaint.measureText(text), textCurvatureDegrees) else 0f

    /**
     * A curved line needs additional vertical space. Preserve the current text scaling while
     * adding that space, instead of resetting a manually resized text box on every drag event.
     */
    private fun resizeBoxForTextCurvature(previousCurveSagitta: Float) {
        val textHeight = getTextHeight(textPaint.ascent(), textPaint.descent(), multilineText.size)
        val previousContentHeight = (textHeight + previousCurveSagitta).coerceAtLeast(1f)
        val newContentHeight = (textHeight + curveSagittaForPreview()).coerceAtLeast(1f)
        val currentContentHeight = (boxHeight - 2 * BOX_OFFSET).coerceAtLeast(1f)
        boxHeight = currentContentHeight * newContentHeight / previousContentHeight + 2 * BOX_OFFSET
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    private fun getLineHeight(textAscent: Float, textDescent: Float): Float =
        (textDescent - textAscent) * lineSpacingPercent / 100f

    private fun getLineHeight(textAscent: Float, textDescent: Float, lineSpacingPercent: Int): Float =
        (textDescent - textAscent) * lineSpacingPercent / 100f

    private fun getTextHeight(textAscent: Float, textDescent: Float, lineCount: Int): Float {
        val baseLineHeight = textDescent - textAscent
        if (lineCount <= 1) {
            return baseLineHeight
        }
        return baseLineHeight + getLineHeight(textAscent, textDescent) * (lineCount - 1)
    }

    private fun getTextHeight(
        textAscent: Float,
        textDescent: Float,
        lineCount: Int,
        lineSpacingPercent: Int
    ): Float {
        val baseLineHeight = textDescent - textAscent
        if (lineCount <= 1) {
            return baseLineHeight
        }
        return baseLineHeight + getLineHeight(textAscent, textDescent, lineSpacingPercent) *
            (lineCount - 1)
    }

    private fun storeAttributes(italic: Boolean = false) {
        if (italic) {
            boxWidth *= ITALIC_FONT_BOX_ADJUSTMENT
        }
        oldBoxWidth = boxWidth
        oldBoxHeight = boxHeight
        oldToolPosition = PointF(toolPosition.x, toolPosition.y)
    }
    private fun applyAttributes(italic: Boolean = false) {
        boxWidth = oldBoxWidth / if (italic) ITALIC_FONT_BOX_ADJUSTMENT else 1f
        boxHeight = oldBoxHeight
        if (oldToolPosition != null) {
            toolPosition = oldToolPosition as PointF
        } else {
            resetBoxPosition()
        }
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    override fun onSaveInstanceState(bundle: Bundle?) {
        super.onSaveInstanceState(bundle)
        bundle?.apply {
            putBoolean(BUNDLE_TOOL_UNDERLINED, underlined)
            putBoolean(BUNDLE_TOOL_ITALIC, italic)
            putBoolean(BUNDLE_TOOL_BOLD, bold)
            putBoolean(BUNDLE_TOOL_OUTLINE, outline)
            putBoolean(BUNDLE_TOOL_SHADOW, shadow)
            putBoolean(BUNDLE_TOOL_TEXT_BACKGROUND, textBackground)
            putString(BUNDLE_TOOL_TEXT, text)
            putBoolean(BUNDLE_TOOL_TEXT_HAS_BEEN_EDITED, !isInitialText)
            putInt(BUNDLE_TOOL_TEXT_SIZE, textSize)
            putInt(BUNDLE_TOOL_LINE_SPACING_PERCENT, lineSpacingPercent)
            putInt(BUNDLE_TOOL_LETTER_SPACING_PERCENT, letterSpacingPercent)
            putString(BUNDLE_TOOL_FONT, font.builtInFont?.name ?: ZaintFontFamily.SANS_SERIF.name)
            putString(BUNDLE_TOOL_CUSTOM_FONT_NAME, font.displayName)
            putString(BUNDLE_TOOL_CUSTOM_FONT_URI, font.fileUri)
            putString(BUNDLE_TOOL_TEXT_ALIGNMENT, textAlignment.name)
            putBoolean(BUNDLE_TOOL_TEXT_JUSTIFIED, justified)
            putBoolean(BUNDLE_TOOL_USER_DEFINED_TEXT_BOX, userDefinedTextBox)
            putBoolean(BUNDLE_TOOL_USER_DEFINED_TEXT_BOX_HEIGHT, userDefinedTextBoxHeight)
            putFloat(BUNDLE_TOOL_TEXT_CURVATURE_DEGREES, textCurvatureDegrees)
        }
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        bundle?.apply {
            underlined = getBoolean(BUNDLE_TOOL_UNDERLINED, underlined)
            italic = getBoolean(BUNDLE_TOOL_ITALIC, italic)
            bold = getBoolean(BUNDLE_TOOL_BOLD, bold)
            outline = getBoolean(BUNDLE_TOOL_OUTLINE, outline)
            shadow = getBoolean(BUNDLE_TOOL_SHADOW, shadow)
            textBackground = getString("BUNDLE_TOOL_TEXT_BACKGROUND_STYLE")
                ?.let { it != "NONE" }
                ?: getBoolean(BUNDLE_TOOL_TEXT_BACKGROUND, false)
            text = getString(BUNDLE_TOOL_TEXT, text)
            isInitialText = !getBoolean(BUNDLE_TOOL_TEXT_HAS_BEEN_EDITED, !isInitialText)
            textSize = getInt(BUNDLE_TOOL_TEXT_SIZE, textSize)
            lineSpacingPercent = getInt(BUNDLE_TOOL_LINE_SPACING_PERCENT, lineSpacingPercent)
            letterSpacingPercent = getInt(BUNDLE_TOOL_LETTER_SPACING_PERCENT, letterSpacingPercent)
            val customFontUri = getString(BUNDLE_TOOL_CUSTOM_FONT_URI)
            font = if (customFontUri != null) {
                GoogleFontRepository.enrichFont(
                    contextCallback.context,
                    TextFont.custom(getString(BUNDLE_TOOL_CUSTOM_FONT_NAME, "") ?: "", customFontUri)
                )
            } else {
                TextFont.builtIn(ZaintFontFamily.savedNameOrDefault(getString(BUNDLE_TOOL_FONT, font.builtInFont?.name ?: ZaintFontFamily.INTER.name)))
            }
            textAlignment = Paint.Align.valueOf(getString(BUNDLE_TOOL_TEXT_ALIGNMENT, textAlignment.name))
            justified = getBoolean(BUNDLE_TOOL_TEXT_JUSTIFIED, justified)
            userDefinedTextBox = getBoolean(BUNDLE_TOOL_USER_DEFINED_TEXT_BOX, userDefinedTextBox)
            userDefinedTextBoxHeight = getBoolean(BUNDLE_TOOL_USER_DEFINED_TEXT_BOX_HEIGHT, userDefinedTextBoxHeight)
            textCurvatureDegrees = getFloat(BUNDLE_TOOL_TEXT_CURVATURE_DEGREES, textCurvatureDegrees)
        }
        normalizeFontStyle()
        normalizeTextEffect()
        if (!canCurveText()) textCurvatureDegrees = 0f
        textToolOptionsView.setState(bold, italic, underlined, text, textSize, font, lineSpacingPercent, letterSpacingPercent, textAlignment, justified, outline, shadow, textBackground)
        textPaint.isUnderlineText = underlined
        textPaint.isFakeBoldText = false
        textPaint.textAlign = textAlignment
        updateActiveLetterSpacing()
        updateTypeface()
    }

    private fun normalizeFontStyle() {
        if (font.supportsStyle(bold, italic)) {
            return
        }
        when {
            bold && font.supportsStyle(true, false) -> italic = false
            italic && font.supportsStyle(false, true) -> bold = false
            else -> {
                bold = false
                italic = false
            }
        }
    }

    private fun normalizeTextEffect() {
        val textEffect = when {
            outline -> ZaintTextEffect.OUTLINE
            shadow -> ZaintTextEffect.SHADOW
            textBackground -> ZaintTextEffect.BACKGROUND
            else -> ZaintTextEffect.NONE
        }
        outline = textEffect == ZaintTextEffect.OUTLINE
        shadow = textEffect == ZaintTextEffect.SHADOW
        textBackground = textEffect == ZaintTextEffect.BACKGROUND
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun updateTypeface() {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        textPaint.textSkewX = DEFAULT_TEXT_SKEW
        textPaint.isFakeBoldText = false
        val builtInFont = font.builtInFont
        when {
            font.isCustom -> textPaint.typeface = GoogleFontRepository.typefaceFor(contextCallback.context, font, style)
            builtInFont == ZaintFontFamily.SANS_SERIF -> textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, style)
            builtInFont == ZaintFontFamily.SERIF -> textPaint.typeface = Typeface.create(Typeface.SERIF, style)
            builtInFont == ZaintFontFamily.MONOSPACE -> textPaint.typeface = Typeface.create(Typeface.MONOSPACE, style)
            else -> {
                val fontResource = builtInFont?.fontResourceFor(bold, italic) ?: return
                try {
                    textPaint.typeface = contextCallback.getFont(fontResource)
                } catch (e: Exception) {
                    Log.e(TAG, builtInFont?.name.orEmpty())
                }
            }
        }
    }

    private fun changeTextColor() {
        val width = boxWidth
        val height = boxHeight
        val position = PointF(toolPosition.x, toolPosition.y)
        textPaint.color = toolPaint.previewColor
        toolPosition.set(position)
        boxWidth = width
        boxHeight = height
        workspace.invalidate()
    }

    override fun resetInternalState() = Unit

    override fun onClickOnButton() = insertIntoLayer()

    private fun insertIntoLayer() {
        highlightBox()
        val toolPosition = PointF(toolPosition.x, toolPosition.y)

        val typeFaceInfo = ZaintTextStyle(
            font.builtInFont ?: ZaintFontFamily.SANS_SERIF,
            bold,
            underlined,
            italic,
            textPaint.textSize,
            textPaint.textSkewX,
            textAlignment,
            outline,
            shadow,
            lineSpacingPercent,
            textBackground,
            font.displayName,
            font.fileUri,
            letterSpacingPercent,
            justified
        )

        val command = commandFactory.createTextToolCommand(
            multilineText,
            textPaint,
            BOX_OFFSET,
            boxWidth,
            boxHeight,
            toolPosition,
            boxRotation,
            textCurvatureDegrees,
            typeFaceInfo
        )
        commandManager.addCommand(
            if (AutomaticLayerInsertion.shouldCreateNewLayer(workspace.layerModel.layerCount) {
                    overlapsVisibleCurrentLayerPixels(
                        VisiblePixelOverlap.transformedBounds(
                            toolPosition,
                            boxWidth,
                            boxHeight,
                            boxRotation,
                            max(boxWidth, boxHeight) * 0.15f + 4f
                        )
                    ) { canvas -> command.run(canvas, workspace.layerModel) }
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
        textToolOptionsView.setState(
            bold,
            italic,
            underlined,
            text,
            textSize,
            font,
            lineSpacingPercent,
            letterSpacingPercent,
            textAlignment,
            justified,
            outline,
            shadow,
            textBackground
        )
    }

    @VisibleForTesting
    fun resetBoxPosition() {
        if (workspace.scale <= 1) {
            toolPosition.x = workspace.width / 2.0f
            toolPosition.y = boxHeight / 2.0f + MARGIN_TOP
        }
    }

    private fun centerJustifiedTextBoxIfNarrowerThanCanvas() {
        if (boxWidth < workspace.width) {
            toolPosition.x = workspace.width / 2.0f
        }
    }

    override fun changePaintColor(color: Int, invalidate: Boolean) {
        super.changePaintColor(color, invalidate)
        changeTextColor()
    }

    override fun onShapeSizeChanged(shapeText: String) {
        textToolOptionsView.setShapeSizeText(shapeText)
    }

    private fun updateActiveLetterSpacing() {
        textPaint.letterSpacing = if (multilineText.size == 1) {
            letterSpacingPercent / 100f
        } else {
            0f
        }
    }

    override fun onToggleVisibility(isVisible: Boolean) {
        textToolOptionsView.toggleShapeSizeVisibility(isVisible)
    }
}
