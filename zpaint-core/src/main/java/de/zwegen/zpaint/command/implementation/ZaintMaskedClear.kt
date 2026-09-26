package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import de.zwegen.zpaint.ZPaintApplication
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.clipboard.AndroidClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardUndoSnapshot
import de.zwegen.zpaint.contract.ZaintLayerContracts
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Clears an alpha-masked rectangle on the active layer and retains the mask for undo replay. */
class ZaintMaskedClear(
    mask: Bitmap,
    private val bounds: Rect,
    private val bitmapStorage: ClipboardBitmapStorage = AndroidClipboardBitmapStorage { ZPaintApplication.cacheDir }
) : Command {
    private val undoSnapshot = ClipboardUndoSnapshot(mask, bitmapStorage)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val mask = undoSnapshot.bitmapForDrawing() ?: return
        layerModel.currentLayer?.bitmap?.let { target -> clearMaskedPixels(target, mask) }
        undoSnapshot.persist(mask, bounds.width().toFloat(), bounds.height().toFloat())
        undoSnapshot.release(mask)
    }

    override fun freeResources() = undoSnapshot.free()

    private fun clearMaskedPixels(target: Bitmap, mask: Bitmap) {
        require(mask.width == bounds.width() && mask.height == bounds.height()) {
            "Clear mask dimensions must match its bounds"
        }
        val left = max(0, bounds.left)
        val top = max(0, bounds.top)
        val right = min(target.width, bounds.right)
        val bottom = min(target.height, bounds.bottom)
        if (left >= right || top >= bottom) return

        val width = right - left
        val height = bottom - top
        val targetPixels = IntArray(width * height)
        val maskPixels = IntArray(mask.width * mask.height)
        target.getPixels(targetPixels, 0, width, left, top, width, height)
        mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)
        val maskOffsetX = left - bounds.left
        val maskOffsetY = top - bounds.top

        targetPixels.indices.forEach { index ->
            val maskX = index % width + maskOffsetX
            val maskY = index / width + maskOffsetY
            val maskAlpha = maskPixels[maskY * mask.width + maskX] ushr 24
            if (maskAlpha == 0) return@forEach

            val destination = targetPixels[index]
            val destinationAlpha = destination ushr 24
            val outputAlpha = destinationAlpha * (255 - maskAlpha) / 255
            targetPixels[index] = if (outputAlpha == 0) 0 else {
                (outputAlpha shl 24) or (destination and 0x00ffffff)
            }
        }
        target.setPixels(targetPixels, 0, width, left, top, width, height)
    }

    internal fun storedFileForTesting(): File? = undoSnapshot.storedFile
}
