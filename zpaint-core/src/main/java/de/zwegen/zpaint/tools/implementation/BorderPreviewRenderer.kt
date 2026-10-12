package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/** Preview-only decoration; never draws into a document layer. */
internal class BorderPreviewRenderer {
    private val addedShader = checkerShader(Color.WHITE, Color.rgb(203, 203, 203))
    private val croppedShader = checkerShader(Color.rgb(255, 199, 199), Color.rgb(215, 139, 139))
    private val checkerPaint = Paint().apply { isFilterBitmap = false }
    private val colorPaint = Paint()
    private val shaderMatrix = Matrix()
    private val borderPath = Path().apply { fillType = Path.FillType.EVEN_ODD }

    fun draw(canvas: Canvas, width: Int, height: Int, amount: Int, color: Int, scale: Float) {
        if (amount == 0 || width < 1 || height < 1) return
        val imageBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val newBounds = RectF(imageBounds).apply { inset(-amount.toFloat(), -amount.toFloat()) }
        if (newBounds.width() < 1f || newBounds.height() < 1f) return
        val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
        shaderMatrix.setScale(CHECKER_CELL_SCREEN_SIZE / safeScale, CHECKER_CELL_SCREEN_SIZE / safeScale)
        val shader = if (amount > 0) addedShader else croppedShader
        shader.setLocalMatrix(shaderMatrix)
        val outer = if (amount > 0) newBounds else imageBounds
        val inner = if (amount > 0) imageBounds else newBounds
        borderPath.rewind()
        borderPath.fillType = Path.FillType.EVEN_ODD
        borderPath.addRect(outer, Path.Direction.CW)
        borderPath.addRect(inner, Path.Direction.CW)
        val saved = canvas.save()
        try {
            canvas.clipPath(borderPath)
            checkerPaint.shader = shader
            checkerPaint.alpha = if (amount > 0) 255 else CROP_PATTERN_ALPHA
            canvas.drawRect(outer, checkerPaint)
            if (amount > 0) {
                colorPaint.color = color
                canvas.drawRect(outer, colorPaint)

            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun checkerShader(light: Int, dark: Int): BitmapShader {
        val tile = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        tile.setPixels(intArrayOf(light, dark, dark, light), 0, 2, 0, 0, 2, 2)
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private companion object {
        const val CHECKER_CELL_SCREEN_SIZE = 24f
        const val CROP_PATTERN_ALPHA = 70
    }
}
