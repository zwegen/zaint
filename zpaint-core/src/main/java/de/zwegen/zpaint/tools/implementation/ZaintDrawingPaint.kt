package de.zwegen.zpaint.tools.implementation

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import de.zwegen.zpaint.R
import de.zwegen.zpaint.colorpicker.R as ColorPickerR
import de.zwegen.zpaint.tools.ToolPaint

/** Keeps a tool's real paint and preview paint synchronized in Zaint. */
class ZaintDrawingPaint(context: Context) : ToolPaint {
    private val eraseMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val canvasPaint = newStrokePaint()
    private val previewState = Paint()
    private val checkerboard = BitmapFactory.decodeResource(
        context.resources,
        ColorPickerR.drawable.zpaint_checkeredbg
    )?.let { bitmap ->
        BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    init {
        updatePreview()
    }

    override var paint: Paint
        get() = canvasPaint
        set(value) {
            canvasPaint.set(value)
            updatePreview()
        }

    override val previewPaint: Paint
        get() = previewState

    override var color: Int
        get() = canvasPaint.color
        set(value) {
            canvasPaint.color = value
            canvasPaint.xfermode = if (Color.alpha(value) == 0) eraseMode else null
            updatePreview()
        }

    override var strokeWidth: Float
        get() = canvasPaint.strokeWidth
        set(value) {
            canvasPaint.strokeWidth = value
            updateAntialiasing()
            updatePreview()
        }

    override var strokeCap: Paint.Cap
        get() = canvasPaint.strokeCap
        set(value) {
            canvasPaint.strokeCap = value
            updatePreview()
        }

    override val previewColor: Int
        get() = previewState.color

    override val eraseXfermode: PorterDuffXfermode
        get() = eraseMode

    override val checkeredShader: Shader?
        get() = checkerboard

    override fun setAntialiasing() {
        updateAntialiasing()
        updatePreview()
    }

    private fun newStrokePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = DEFAULT_STROKE_WIDTH
        isAntiAlias = antialiasing && strokeWidth > MIN_ANTIALIAS_WIDTH
    }

    private fun updateAntialiasing() {
        canvasPaint.isAntiAlias = antialiasing && canvasPaint.strokeWidth > MIN_ANTIALIAS_WIDTH
    }

    private fun updatePreview() {
        previewState.set(canvasPaint)
        if (Color.alpha(canvasPaint.color) == 0) {
            previewState.xfermode = null
            previewState.shader = checkerboard
            previewState.color = Color.BLACK
            previewState.alpha = Color.BLACK.ushr(24)
        } else {
            previewState.shader = null
        }
    }

    companion object {
        private const val DEFAULT_STROKE_WIDTH = 25f
        private const val MIN_ANTIALIAS_WIDTH = 1f

        var antialiasing: Boolean = true
    }
}
