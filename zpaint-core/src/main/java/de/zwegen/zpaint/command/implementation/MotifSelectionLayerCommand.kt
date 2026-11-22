package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.ZPaintApplication
import de.zwegen.zpaint.command.clipboard.AndroidClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardUndoSnapshot
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.motif.MotifSelectionTarget

/** Replay against the same source layer in history, without ever drawing on that layer. */
internal class MotifSelectionLayerCommand(
    mask: Bitmap,
    storage: ClipboardBitmapStorage = AndroidClipboardBitmapStorage { ZPaintApplication.cacheDir },
    private val target: MotifSelectionTarget = MotifSelectionTarget.OBJECTS
) : SelectionLayerCommand() {
    private val snapshot = ClipboardUndoSnapshot(mask, storage)

    override fun createCutout(source: Bitmap, model: ZaintLayerContracts.Model): Bitmap? {
        val mask = snapshot.bitmapForDrawing() ?: return null
        try {
            if (mask.width != source.width || mask.height != source.height) return null
            if (target == MotifSelectionTarget.BACKGROUND && !mask.containsVisiblePixel()) return null
            snapshot.persist(mask, mask.width.toFloat(), mask.height.toFloat())
            return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                target.drawCutout(Canvas(it), source, mask)
            }
        } finally {
            snapshot.release(mask)
        }
    }

    override fun freeResources() = snapshot.free()
}
