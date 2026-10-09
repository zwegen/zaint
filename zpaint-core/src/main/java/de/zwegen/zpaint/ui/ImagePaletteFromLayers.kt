package de.zwegen.zpaint.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import de.zwegen.zpaint.colorpicker.ImageColorPalette
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Samples only the visible picture, never the full-size canvas or editor decorations. */
internal object ImagePaletteFromLayers {
    private const val MAX_SAMPLE_SIZE = 128

    fun extract(model: ZaintLayerContracts.Model): List<Int> {
        if (model.width <= 0 || model.height <= 0) return emptyList()
        val scale = minOf(1.0, MAX_SAMPLE_SIZE.toDouble() / maxOf(model.width, model.height))
        val width = (model.width * scale).toInt().coerceAtLeast(1)
        val height = (model.height * scale).toInt().coerceAtLeast(1)
        val sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(sample)
            canvas.scale(width.toFloat() / model.width, height.toFloat() / model.height)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val layers = model.listIterator(0).asSequence().toList()
            for (layer in layers.asReversed()) {
                if (!layer.isVisible || layer.bitmap.isRecycled) continue
                paint.alpha = layer.getValueForOpacityPercentage()
                canvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
            }
            val pixels = IntArray(width * height)
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
            return ImageColorPalette.extract(pixels)
        } finally {
            sample.recycle()
        }
    }
}
