package de.zwegen.zpaint.command.clipboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Stores bitmap snapshots used by clipboard commands after their first execution. */
interface ClipboardBitmapStorage {
    fun load(file: File): Bitmap?

    fun store(bitmap: Bitmap, requestedWidth: Float, requestedHeight: Float): File

    fun delete(file: File)
}

/** Android file-backed implementation kept at the command boundary. */
class AndroidClipboardBitmapStorage(
    private val cacheDirectory: () -> File?
) : ClipboardBitmapStorage {
    override fun load(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply { inMutable = true }
        return BitmapFactory.decodeFile(file.absolutePath, options)?.also { it.setHasAlpha(true) }
    }

    override fun store(bitmap: Bitmap, requestedWidth: Float, requestedHeight: Float): File {
        val storageDirectory = cacheDirectory() ?: File(".")
        storageDirectory.mkdirs()
        val storedFile = File(storageDirectory, "clipboard-${UUID.randomUUID()}.png")
        val storedBitmap = bitmap.scaledToFit(requestedWidth, requestedHeight)
        try {
            FileOutputStream(storedFile).use { output ->
                check(storedBitmap.compress(Bitmap.CompressFormat.PNG, COMPRESSION_QUALITY, output)) {
                    "Could not store clipboard bitmap"
                }
            }
        } finally {
            if (storedBitmap !== bitmap && !storedBitmap.isRecycled) {
                storedBitmap.recycle()
            }
        }
        return storedFile
    }

    override fun delete(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    private fun Bitmap.scaledToFit(requestedWidth: Float, requestedHeight: Float): Bitmap {
        val targetWidth = minOf(width, requestedWidth.toInt())
        val targetHeight = minOf(height, requestedHeight.toInt())
        return if (targetWidth == width && targetHeight == height) {
            this
        } else {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, false)
        }
    }

    private companion object {
        const val COMPRESSION_QUALITY = 100
    }
}
