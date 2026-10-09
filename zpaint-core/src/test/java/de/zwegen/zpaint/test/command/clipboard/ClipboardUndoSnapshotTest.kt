package de.zwegen.zpaint.test.command.clipboard

import android.graphics.Bitmap
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardUndoSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class ClipboardUndoSnapshotTest {
    @Test
    fun `persists the first undo snapshot only`() {
        val storage = RecordingStorage(File("clipboard-snapshot"))
        val snapshot = ClipboardUndoSnapshot(mock(Bitmap::class.java), storage)

        snapshot.persist(mock(Bitmap::class.java), 20f, 30f)
        snapshot.persist(mock(Bitmap::class.java), 20f, 30f)

        assertEquals(1, storage.storeCount)
        assertEquals(storage.storedFile, snapshot.storedFile)
    }

    private class RecordingStorage(val storedFile: File) : ClipboardBitmapStorage {
        var storeCount = 0

        override fun load(file: File): Bitmap? = null

        override fun store(bitmap: Bitmap, requestedWidth: Float, requestedHeight: Float): File {
            storeCount++
            return storedFile
        }

        override fun delete(file: File) = Unit
    }
}
