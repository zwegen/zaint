package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Point
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.implementation.ZaintClipboardStamp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class ZaintClipboardStampStorageTest {
    @Test
    fun storesUndoSnapshotThroughTheProvidedStorage() {
        val storage = RecordingStorage(File("clipboard-snapshot"))
        val command = ZaintClipboardStamp(
            mock(Bitmap::class.java),
            Point(4, 7),
            20f,
            30f,
            0f,
            storage
        )

        command.storeBitmap(mock(Bitmap::class.java), 20f, 30f)

        assertEquals(storage.storedFile, command.fileToStoredBitmap)
    }

    private class RecordingStorage(val storedFile: File) : ClipboardBitmapStorage {
        override fun load(file: File): Bitmap? = null

        override fun store(bitmap: Bitmap, requestedWidth: Float, requestedHeight: Float): File = storedFile

        override fun delete(file: File) = Unit
    }
}
