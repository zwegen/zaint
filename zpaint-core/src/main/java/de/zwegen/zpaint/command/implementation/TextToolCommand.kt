package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.serialization.ZaintTextStyle
import de.zwegen.zpaint.common.ITALIC_FONT_BOX_ADJUSTMENT
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.TextCurveRenderer

private const val TEXT_OUTLINE_WIDTH_RATIO = 0.08f
private const val TEXT_SHADOW_BLUR_RATIO = 0.12f
private const val TEXT_SHADOW_ALPHA = 150
private const val CONTRAST_BACKGROUND_LUMINANCE_THRESHOLD = 0.55f
private const val TEXT_BACKGROUND_PADDING_RATIO = 0.18f
private const val LAST_LINE_JUSTIFY_THRESHOLD = 0.78f

class ZaintTextStamp(
    multilineText: Array<String>,
    textPaint: Paint,
    boxOffset: Float,
    boxWidth: Float,
    boxHeight: Float,
    toolPosition: PointF,
    rotationAngle: Float,
    curvatureDegrees: Float,
    typeFaceInfo: ZaintTextStyle
) : Command {

    var multilineText = multilineText.clone(); private set
    var textPaint = textPaint; private set
    var boxOffset = boxOffset; private set
    var boxWidth = boxWidth; private set
    var boxHeight = boxHeight; private set
    var toolPosition = toolPosition; private set
    var rotationAngle = rotationAngle; private set
    var curvatureDegrees = curvatureDegrees; private set
    var typeFaceInfo = typeFaceInfo; private set

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        textPaint.textAlign = typeFaceInfo.textAlignment
        textPaint.letterSpacing = if (multilineText.size == 1) {
            typeFaceInfo.letterSpacingPercent / 100f
        } else {
            0f
        }
        val textAscent = textPaint.ascent()
        val textDescent = textPaint.descent()
        val lineHeight = getLineHeight(textAscent, textDescent)
        val textHeight = getTextHeight(textAscent, textDescent, multilineText.size)
        var maxTextWidth = multilineText.maxOf { line ->
            textPaint.measureText(line)
        }

        if (typeFaceInfo.italic) {
            maxTextWidth *= ITALIC_FONT_BOX_ADJUSTMENT
        }

        with(canvas) {
            save()
            translate(toolPosition.x, toolPosition.y)
            rotate(rotationAngle)

            val widthScaling = (boxWidth - 2 * boxOffset) / maxTextWidth
            val curveSagitta = if (multilineText.size == 1) {
                TextCurveRenderer.sagitta(textPaint.measureText(multilineText[0]), curvatureDegrees)
            } else {
                0f
            }
            val heightScaling = (boxHeight - 2 * boxOffset) / (textHeight + curveSagitta)
            canvas.scale(widthScaling, heightScaling)

            val scaledHeightOffset = boxOffset / heightScaling
            val scaledWidthOffset = boxOffset / widthScaling
            val scaledBoxWidth = boxWidth / widthScaling
            val scaledBoxHeight = boxHeight / heightScaling

            if (typeFaceInfo.textBackground) {
                drawTextBackground(canvas, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, maxTextWidth, textAscent, textDescent, lineHeight)
            }

            multilineText.forEachIndexed { index, textLine ->
                if (typeFaceInfo.shadow) {
                    drawTextLine(canvas, textLine, index, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, textAscent, lineHeight, maxTextWidth, createShadowPaint())
                }
                if (typeFaceInfo.outline) {
                    drawTextLine(canvas, textLine, index, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, textAscent, lineHeight, maxTextWidth, createOutlinePaint())
                }
                drawTextLine(canvas, textLine, index, scaledWidthOffset, scaledHeightOffset, scaledBoxWidth, scaledBoxHeight, textAscent, lineHeight, maxTextWidth, textPaint)
            }
            restore()
        }
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
        if (multilineText.size == 1 && curvatureDegrees != 0f) {
            val textLeft = getAlignedTextLeft(scaledWidthOffset, scaledBoxWidth, paint.measureText(textLine))
            val sagitta = TextCurveRenderer.sagitta(paint.measureText(textLine), curvatureDegrees)
            // Match the preview: curved glyphs are placed by their visual centre, not by a
            // Canvas text baseline. Otherwise the committed text shifts vertically.
            val arcEndpointCenterY = -(scaledBoxHeight / 2) + scaledHeightOffset +
                (paint.descent() - paint.ascent()) / 2f +
                if (curvatureDegrees < 0f) sagitta else 0f
            TextCurveRenderer.draw(
                canvas,
                textLine,
                paint,
                textLeft + paint.measureText(textLine) / 2f,
                arcEndpointCenterY,
                curvatureDegrees
            )
            return
        }
        val textX = getAlignedTextX(scaledWidthOffset, scaledBoxWidth)
        val baseline = -(scaledBoxHeight / 2) + scaledHeightOffset - textAscent + lineHeight * index
        if (typeFaceInfo.justified && shouldJustifyLine(index, textLine, justifiedWidth, paint)) {
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
        val textLeft = getAlignedTextLeft(scaledWidthOffset, scaledBoxWidth, maxTextWidth)
        val curveSagitta = if (multilineText.size == 1) {
            TextCurveRenderer.sagitta(maxTextWidth, curvatureDegrees)
        } else {
            0f
        }
        val firstBaseline = -(scaledBoxHeight / 2) + scaledHeightOffset - textAscent +
            if (curvatureDegrees < 0f) curveSagitta else 0f
        val padding = textPaint.textSize * TEXT_BACKGROUND_PADDING_RATIO
        val top = firstBaseline + textAscent - padding - if (curvatureDegrees < 0f) curveSagitta else 0f
        val bottom = firstBaseline + lineHeight * (multilineText.size - 1) + textDescent + padding +
            if (curvatureDegrees > 0f) curveSagitta else 0f
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

    private fun getAlignedTextX(scaledWidthOffset: Float, scaledBoxWidth: Float): Float {
        val italicAdjustment = if (typeFaceInfo.italic) ITALIC_FONT_BOX_ADJUSTMENT else 1f
        return when (typeFaceInfo.textAlignment) {
            Paint.Align.CENTER -> 0f
            Paint.Align.RIGHT -> scaledBoxWidth / 2 / italicAdjustment - scaledWidthOffset
            else -> scaledWidthOffset - scaledBoxWidth / 2 / italicAdjustment
        }
    }

    private fun getAlignedTextLeft(
        scaledWidthOffset: Float,
        scaledBoxWidth: Float,
        maxTextWidth: Float
    ): Float {
        val textX = getAlignedTextX(scaledWidthOffset, scaledBoxWidth)
        return when (typeFaceInfo.textAlignment) {
            Paint.Align.CENTER -> textX - maxTextWidth / 2f
            Paint.Align.RIGHT -> textX - maxTextWidth
            else -> textX
        }
    }

    private fun getLineHeight(textAscent: Float, textDescent: Float): Float =
        (textDescent - textAscent) * typeFaceInfo.lineSpacingPercent / 100f

    private fun getTextHeight(textAscent: Float, textDescent: Float, lineCount: Int): Float {
        val baseLineHeight = textDescent - textAscent
        if (lineCount <= 1) {
            return baseLineHeight
        }
        return baseLineHeight + getLineHeight(textAscent, textDescent) * (lineCount - 1)
    }

    override fun freeResources() {
        // No resources to free
    }
}
