package de.zwegen.zpaint.ui.tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatEditText

/**
 * Keeps Android's normal text input behaviour, but draws justified text as one centred block.
 * This is necessary because EditText's built-in justification always starts the block at the left.
 */
internal class ZaintJustifiedEditText(context: Context) : AppCompatEditText(context) {
    var isJustifiedPreviewEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(CURSOR_WIDTH_DP).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        if (!isJustifiedPreviewEnabled) {
            super.onDraw(canvas)
            return
        }

        val lines = text.toString().split('\n')
        val contentWidth = (width - paddingLeft - paddingRight).toFloat().coerceAtLeast(0f)
        val blockWidth = lines.maxOfOrNull(paint::measureText)
            ?.coerceAtMost(contentWidth)
            ?: 0f
        val blockLeft = paddingLeft + (contentWidth - blockWidth) / 2f
        val lineHeight = paint.fontMetrics.run { bottom - top }
        var baseline = paddingTop - paint.fontMetrics.top

        lines.forEachIndexed { index, line ->
            if (shouldJustifyLine(index, lines.lastIndex, line, blockWidth)) {
                drawJustifiedLine(canvas, line, blockLeft, baseline, blockWidth)
            } else {
                canvas.drawText(line, blockLeft, baseline, paint)
            }
            baseline += lineHeight
        }
        drawCursor(canvas, lines, blockLeft, blockWidth, lineHeight)
    }

    private fun drawJustifiedLine(
        canvas: Canvas,
        line: String,
        left: Float,
        baseline: Float,
        targetWidth: Float
    ) {
        val words = line.trim().split(WHITESPACE).filter(String::isNotEmpty)
        if (words.size < 2) {
            canvas.drawText(line, left, baseline, paint)
            return
        }

        val extraSpacing = (targetWidth - paint.measureText(line)) / (words.size - 1)
        if (extraSpacing <= 0f) {
            canvas.drawText(line, left, baseline, paint)
            return
        }

        var wordLeft = left
        val spaceWidth = paint.measureText(" ")
        words.forEachIndexed { index, word ->
            canvas.drawText(word, wordLeft, baseline, paint)
            wordLeft += paint.measureText(word)
            if (index < words.lastIndex) {
                wordLeft += spaceWidth + extraSpacing
            }
        }
    }

    private fun drawCursor(
        canvas: Canvas,
        lines: List<String>,
        blockLeft: Float,
        blockWidth: Float,
        lineHeight: Float
    ) {
        if (!isFocused) return

        var remaining = selectionEnd.coerceIn(0, text?.length ?: 0)
        var lineIndex = 0
        for (index in lines.indices) {
            val line = lines[index]
            if (remaining <= line.length) {
                lineIndex = index
                break
            }
            remaining -= line.length + 1
        }
        val line = lines[lineIndex]
        val justified = shouldJustifyLine(lineIndex, lines.lastIndex, line, blockWidth)
        val cursorX = cursorXFor(line, remaining.coerceIn(0, line.length), blockLeft, blockWidth, justified)
        val cursorTop = paddingTop + lineIndex * lineHeight
        cursorPaint.color = currentTextColor
        canvas.drawLine(cursorX, cursorTop, cursorX, cursorTop + lineHeight, cursorPaint)
    }

    private fun cursorXFor(
        line: String,
        characterIndex: Int,
        left: Float,
        targetWidth: Float,
        justified: Boolean
    ): Float {
        if (!justified) return left + paint.measureText(line.take(characterIndex))
        val words = line.trim().split(WHITESPACE).filter(String::isNotEmpty)
        if (words.size < 2) return left + paint.measureText(line.take(characterIndex))
        val extraSpacing = (targetWidth - paint.measureText(line)) / (words.size - 1)
        if (extraSpacing <= 0f) return left + paint.measureText(line.take(characterIndex))
        val gapsBeforeCursor = line.take(characterIndex).count(Char::isWhitespace)
        return left + paint.measureText(line.take(characterIndex)) + gapsBeforeCursor * extraSpacing
    }

    private fun shouldJustifyLine(index: Int, lastIndex: Int, line: String, targetWidth: Float): Boolean =
        index < lastIndex || paint.measureText(line) >= targetWidth * LAST_LINE_JUSTIFY_THRESHOLD

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val LAST_LINE_JUSTIFY_THRESHOLD = 0.78f
        const val CURSOR_WIDTH_DP = 2
        val WHITESPACE = Regex("\\s+")
    }
}
