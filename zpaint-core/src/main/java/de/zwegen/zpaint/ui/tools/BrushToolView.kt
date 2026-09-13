package de.zwegen.zpaint.ui.tools

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import de.zwegen.zpaint.R
import de.zwegen.zpaint.colorpicker.R as ColorPickerR
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.options.ZaintBrushOptions.PreviewState
import de.zwegen.zpaint.tools.options.BrushToolPreview

/** Draws the current brush or line style in Zaint's existing option panels. */
class BrushToolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), BrushToolPreview {
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val transparentStrokeShader: Shader = BitmapShader(
        requireNotNull(BitmapFactory.decodeResource(resources, ColorPickerR.drawable.zpaint_checkeredbg)),
        Shader.TileMode.REPEAT,
        Shader.TileMode.REPEAT
    )
    private var listener: PreviewState? = null

    override fun setListener(callback: PreviewState) {
        listener = callback
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (listener?.toolType == ZaintToolKind.BRUSH || listener?.toolType == ZaintToolKind.LINE) {
            drawStrokePreview(canvas)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(measuredWidth, (measuredWidth * HEIGHT_TO_WIDTH_RATIO).toInt())
    }

    private fun drawStrokePreview(canvas: Canvas) {
        val state = listener ?: return
        previewPaint.reset()
        previewPaint.isAntiAlias = true
        previewPaint.style = Paint.Style.STROKE
        previewPaint.strokeWidth = state.strokeWidth
        previewPaint.strokeCap = state.strokeCap
        previewPaint.maskFilter = state.maskFilter
        if (Color.alpha(state.color) == 0) {
            previewPaint.color = Color.BLACK
            previewPaint.shader = transparentStrokeShader
        } else {
            previewPaint.color = state.color
        }

        val baseline = height - PREVIEW_BOTTOM_OFFSET
        canvas.drawLine(
            width * PREVIEW_START_FRACTION,
            baseline,
            width * PREVIEW_END_FRACTION,
            baseline,
            previewPaint
        )
    }

    private companion object {
        const val HEIGHT_TO_WIDTH_RATIO = 0.25f
        const val PREVIEW_START_FRACTION = 2f / 3f
        const val PREVIEW_END_FRACTION = 15f / 16f
        const val PREVIEW_BOTTOM_OFFSET = 56f
    }
}
