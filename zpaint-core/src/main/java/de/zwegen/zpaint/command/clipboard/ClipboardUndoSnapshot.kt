package de.zwegen.zpaint.command.clipboard

import android.graphics.Bitmap
import java.io.File

/**
 * Keeps a clipboard bitmap in memory for its first execution and as a file for later undo replay.
 */
class ClipboardUndoSnapshot(
    bitmap: Bitmap,
    private val storage: ClipboardBitmapStorage
) {
    var inMemoryBitmap: Bitmap? = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        private set

    var storedFile: File? = null

    fun bitmapForDrawing(): Bitmap? = storedFile?.let(storage::load) ?: inMemoryBitmap

    fun persist(bitmap: Bitmap, requestedWidth: Float, requestedHeight: Float) {
        if (storedFile == null) {
            storedFile = storage.store(bitmap, requestedWidth, requestedHeight)
        }
    }

    fun release(bitmap: Bitmap?) {
        bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        if (bitmap === inMemoryBitmap) {
            inMemoryBitmap = null
        }
    }

    fun free() {
        release(inMemoryBitmap)
        storedFile?.let(storage::delete)
    }
}
