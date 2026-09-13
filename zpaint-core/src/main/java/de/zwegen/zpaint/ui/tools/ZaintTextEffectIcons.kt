package de.zwegen.zpaint.ui.tools

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Draws the text-effect previews directly instead of trying to emulate blur with
 * stacked vector paths.  VectorDrawable has no blur support, which made the
 * previous shadow preview read as an outline.
 */
internal class ZaintSoftShadowEffectIcon : Drawable() {
    private val foregroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        color = Color.BLACK // 100 % opacity
        maskFilter = BlurMaskFilter(1.35f, BlurMaskFilter.Blur.NORMAL)
    }
    private val outerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        color = Color.BLACK // additional 100 % outer shadow
        maskFilter = BlurMaskFilter(2.2f, BlurMaskFilter.Blur.NORMAL)
    }
    private var isChecked = false
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        withViewport(canvas) {
            // The white A has no contour; only the centered shadow remains visible.
            drawSolidLetter(canvas, outerShadowPaint)
            drawSolidLetter(canvas, shadowPaint)
            drawSolidLetter(canvas, foregroundPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        applyColors()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        foregroundPaint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter
        outerShadowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24

    override fun isStateful() = true

    override fun onStateChange(stateSet: IntArray): Boolean {
        val checked = stateSet.any { it == android.R.attr.state_checked }
        if (checked == isChecked) return false
        isChecked = checked
        applyColors()
        invalidateSelf()
        return true
    }

    private fun applyColors() {
        foregroundPaint.color = Color.WHITE
        shadowPaint.color = if (isChecked) Color.WHITE else Color.BLACK
        outerShadowPaint.color = if (isChecked) Color.WHITE else Color.BLACK
        foregroundPaint.alpha = drawableAlpha
        shadowPaint.alpha = drawableAlpha
        outerShadowPaint.alpha = drawableAlpha
    }
}

internal class ZaintBackgroundEffectIcon : Drawable() {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private var isChecked = false
    private var drawableAlpha = 255
    override fun draw(canvas: Canvas) {
        withViewport(canvas) {
            // The black field leaves 2 dp around the A. The A is a solid glyph, rather
            // than the outline construction used for the first effect icon.
            canvas.drawRect(2f, 1.5f, 22f, 22.5f, backgroundPaint)
            drawSolidLetter(canvas, letterPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        applyColors()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        letterPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24

    override fun isStateful() = true

    override fun onStateChange(stateSet: IntArray): Boolean {
        val checked = stateSet.any { it == android.R.attr.state_checked }
        if (checked == isChecked) return false
        isChecked = checked
        applyColors()
        invalidateSelf()
        return true
    }

    private fun applyColors() {
        backgroundPaint.color = if (isChecked) Color.WHITE else Color.BLACK
        letterPaint.color = if (isChecked) Color.BLACK else Color.WHITE
        backgroundPaint.alpha = drawableAlpha
        letterPaint.alpha = drawableAlpha
    }
}

private fun drawSolidLetter(canvas: Canvas, paint: Paint) {
    val target = RectF(3.64f, 3f, 20.36f, 21f)
    paint.textSize = 24f
    val measured = android.graphics.Rect()
    paint.getTextBounds("A", 0, 1, measured)
    val scale = min(target.width() / measured.width(), target.height() / measured.height())
    paint.textSize *= scale
    paint.getTextBounds("A", 0, 1, measured)

    val x = target.centerX() - measured.width() / 2f - measured.left
    val y = target.centerY() - (measured.top + measured.bottom) / 2f
    canvas.drawText("A", x, y, paint)
}

private fun createOriginalLetterPath(): Path = Path().apply {
    moveTo(11f, 3f)
    cubicTo(10.18f, 3f, 9.44f, 3.5f, 9.14f, 4.27f)
    lineTo(3.64f, 18.27f)
    cubicTo(3.12f, 19.58f, 4.09f, 21f, 5.5f, 21f)
    lineTo(7.75f, 21f)
    cubicTo(8.59f, 21f, 9.33f, 20.5f, 9.62f, 19.7f)
    lineTo(10.26f, 18f)
    lineTo(13.74f, 18f)
    lineTo(14.38f, 19.7f)
    cubicTo(14.67f, 20.5f, 15.42f, 21f, 16.25f, 21f)
    lineTo(18.5f, 21f)
    cubicTo(19.91f, 21f, 20.88f, 19.58f, 20.36f, 18.27f)
    lineTo(14.86f, 4.27f)
    cubicTo(14.56f, 3.5f, 13.82f, 3f, 13f, 3f)
    close()

    moveTo(11f, 5f)
    lineTo(13f, 5f)
    lineTo(18.5f, 19f)
    lineTo(16.25f, 19f)
    lineTo(15.12f, 16f)
    lineTo(8.87f, 16f)
    lineTo(7.75f, 19f)
    lineTo(5.5f, 19f)
    close()

    moveTo(12f, 7.67f)
    lineTo(9.62f, 14f)
    lineTo(14.37f, 14f)
    close()

    // Keep the slight horizontal correction used by the original A vector.
    transform(android.graphics.Matrix().apply {
        setScale(1.01351f, 1f)
        postTranslate(-0.16212f, 0f)
    })
}

private inline fun Drawable.withViewport(canvas: Canvas, block: () -> Unit) {
    val viewport = 24f
    val rect = bounds
    val scale = min(rect.width() / viewport, rect.height() / viewport)
    val offsetX = rect.left + (rect.width() - viewport * scale) / 2f
    val offsetY = rect.top + (rect.height() - viewport * scale) / 2f
    canvas.save()
    canvas.translate(offsetX, offsetY)
    canvas.scale(scale, scale)
    block()
    canvas.restore()
}
