package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import de.zwegen.zpaint.ZPaintApplication
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.bitmap.MaskedColorReplacement
import de.zwegen.zpaint.command.clipboard.AndroidClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardUndoSnapshot
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Undoable, file-backed color/alpha replacement; image and clipboard stamps remain SRC_OVER. */
class ZaintMaskedColorFill(
    bitmap: Bitmap,
    mask: Bitmap,
    bounds: Rect,
    bitmapStorage: ClipboardBitmapStorage = AndroidClipboardBitmapStorage { ZPaintApplication.cacheDir }
) : Command {
    private val bounds = Rect(bounds)
    private val fillSnapshot = ClipboardUndoSnapshot(bitmap, bitmapStorage)
    private val maskSnapshot = ClipboardUndoSnapshot(mask, bitmapStorage)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val target = layerModel.currentLayer?.bitmap ?: return
        val fill = fillSnapshot.bitmapForDrawing() ?: return
        val mask = maskSnapshot.bitmapForDrawing()
        try {
            if (mask == null) return
            MaskedColorReplacement.apply(target, fill, mask, bounds)
            fillSnapshot.persist(fill, bounds.width().toFloat(), bounds.height().toFloat())
            maskSnapshot.persist(mask, bounds.width().toFloat(), bounds.height().toFloat())
        } finally {
            fillSnapshot.release(fill)
            maskSnapshot.release(mask)
        }
    }

    override fun freeResources() {
        fillSnapshot.free()
        maskSnapshot.free()
    }
}
