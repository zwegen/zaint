package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.caverock.androidsvg.SVG
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_SVG_SIZE = 1024
private const val MAX_SVG_SIZE = 4096

class SvgBitmapRenderer {
    fun render(svgText: String, size: Int): Bitmap {
        val svg = SVG.getFromString(svgText)
        svg.documentWidth = size.toFloat()
        svg.documentHeight = size.toFloat()
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            svg.renderToCanvas(Canvas(this))
        }
    }

    fun render(svgText: String): Bitmap {
        val svg = SVG.getFromString(svgText)
        val viewBox = svg.documentViewBox
        val sourceWidth = when {
            svg.documentWidth > 0f -> svg.documentWidth
            viewBox != null && viewBox.width() > 0f -> viewBox.width()
            else -> DEFAULT_SVG_SIZE.toFloat()
        }
        val sourceHeight = when {
            svg.documentHeight > 0f -> svg.documentHeight
            viewBox != null && viewBox.height() > 0f -> viewBox.height()
            else -> DEFAULT_SVG_SIZE.toFloat()
        }
        val scale = min(1f, MAX_SVG_SIZE / max(sourceWidth, sourceHeight))
        val width = max(1, (sourceWidth * scale).roundToInt())
        val height = max(1, (sourceHeight * scale).roundToInt())
        svg.documentWidth = width.toFloat()
        svg.documentHeight = height.toFloat()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            svg.renderToCanvas(Canvas(this))
        }
    }
}
