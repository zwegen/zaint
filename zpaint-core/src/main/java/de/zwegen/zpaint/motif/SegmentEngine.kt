package de.zwegen.zpaint.motif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal object SegmentEngine {
    private const val EDGE = MagicTouchInput.EDGE

    // Capture on the editor thread: never let inference retain a live, editable layer.
    fun snapshot(source: Bitmap): IntArray {
        val scaled = Bitmap.createBitmap(EDGE, EDGE, Bitmap.Config.ARGB_8888)
        try {
            Canvas(scaled).drawBitmap(source, null, Rect(0, 0, EDGE, EDGE), Paint(Paint.FILTER_BITMAP_FLAG))
            return IntArray(EDGE * EDGE).also {
                scaled.getPixels(it, 0, EDGE, 0, 0, EDGE, EDGE)
            }
        } finally {
            scaled.recycle()
        }
    }

    fun infer(context: Context, pixels: IntArray, tap: Int): ByteArray {
        val model = context.assets.openFd("motif/magic_touch.tflite").use { asset ->
            FileInputStream(asset.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
            }
        }
        val input = floatBuffer(EDGE * EDGE * MagicTouchInput.CHANNELS)
        val confidence = floatBuffer(EDGE * EDGE)
        MagicTouchInput.encode(pixels, tap, input)
        Interpreter(model, Interpreter.Options().setNumThreads(3)).use { it.run(input, confidence) }
        confidence.rewind()
        return ByteArray(EDGE * EDGE).also { ConfidenceMask.readAlphas(confidence, it) }
    }

    fun expand(matte: ByteArray, width: Int, height: Int): Bitmap? {
        val small = Bitmap.createBitmap(EDGE, EDGE, Bitmap.Config.ARGB_8888)
        try {
            val row = IntArray(EDGE)
            for (y in 0 until EDGE) {
                for (x in row.indices) row[x] = matte[y * EDGE + x].toInt() shl 24
                small.setPixels(row, 0, EDGE, 0, y, EDGE, 1)
            }
            val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(mask).drawBitmap(small, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            // Downsampling can miss a tiny subject entirely. Never invert such an empty mask.
            if ((width < EDGE || height < EDGE) && !hasCoverage(mask)) {
                mask.recycle()
                return null
            }
            return mask
        } finally {
            small.recycle()
        }
    }

    private fun hasCoverage(mask: Bitmap): Boolean {
        val row = IntArray(mask.width)
        for (y in 0 until mask.height) {
            mask.getPixels(row, 0, mask.width, 0, y, mask.width, 1)
            if (row.any { it ushr 24 != 0 }) return true
        }
        return false
    }

    private fun floatBuffer(size: Int) =
        ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
}
