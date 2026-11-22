package de.zwegen.zpaint.motif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import de.zwegen.zpaint.R

enum class MotifSelectionTarget(val label: Int, mode: PorterDuff.Mode) {
    OBJECTS(R.string.selection_motif_objects, PorterDuff.Mode.DST_IN),
    BACKGROUND(R.string.selection_motif_background, PorterDuff.Mode.DST_OUT);

    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(mode) }

    /** Draw onto a transparent layer. Destination alpha is source alpha times m (or 1 − m). */
    internal fun drawCutout(canvas: Canvas, source: Bitmap, mask: Bitmap) {
        val bounds = Rect(0, 0, source.width, source.height)
        canvas.drawBitmap(source, null, bounds, null)
        canvas.drawBitmap(mask, null, bounds, maskPaint)
    }
}
