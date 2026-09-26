package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.implementation.ZaintMaskedClear
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZaintMaskedClearTest {
    @Test
    fun clearsOnlyPixelsCoveredByTheOpaqueMask() {
        val destination = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val model = ZaintLayerModel().apply {
            val layer = ZaintLayer(destination)
            addLayerAt(0, layer)
            currentLayer = layer
        }
        val mask = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

        ZaintMaskedClear(mask, Rect(1, 1, 3, 3), NoOpStorage).run(Canvas(destination), model)

        assertEquals(0, Color.alpha(destination.getPixel(1, 1)))
        assertEquals(Color.GREEN, destination.getPixel(0, 0))
    }

    private object NoOpStorage : ClipboardBitmapStorage {
        override fun load(file: File): Bitmap? = null
        override fun store(bitmap: Bitmap, requestedWidth: Float, requestedHeight: Float): File = File("mask")
        override fun delete(file: File) = Unit
    }
}
