package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.ZPaintApplication
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.clipboard.AndroidClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardUndoSnapshot
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.FilterProcessor
import de.zwegen.zpaint.tools.implementation.FilterType

/** Replays the filter with the same lossless mask used for the full-resolution preview. */
class MotifFilterCommand(
    private val type: FilterType,
    private val value: Int,
    colors: IntArray,
    tolerances: IntArray,
    mask: Bitmap,
    private val background: Boolean,
    storage: ClipboardBitmapStorage = AndroidClipboardBitmapStorage { ZPaintApplication.cacheDir }
) : Command {
    private val colors = colors.copyOf()
    private val tolerances = tolerances.copyOf()
    private val snapshot = ClipboardUndoSnapshot(mask, storage)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        val mask = snapshot.bitmapForDrawing() ?: return
        try {
            snapshot.persist(mask, mask.width.toFloat(), mask.height.toFloat())
            layer.bitmap = FilterProcessor.applyMotif(layer.bitmap, type, value, colors, tolerances, mask, background)
        } finally {
            snapshot.release(mask)
        }
    }

    override fun freeResources() = snapshot.free()
}
