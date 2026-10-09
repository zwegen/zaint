package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.implementation.ZaintLayerStackDocumentLoad
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ZaintLayerStackDocumentLoadTest {
    @Test
    fun `copies layer pixels and presentation state into the document`() {
        val firstBitmap = mock(Bitmap::class.java)
        val firstCopy = mock(Bitmap::class.java)
        val secondBitmap = mock(Bitmap::class.java)
        val secondCopy = mock(Bitmap::class.java)
        `when`(firstBitmap.copy(Bitmap.Config.ARGB_8888, true)).thenReturn(firstCopy)
        `when`(secondBitmap.copy(Bitmap.Config.ARGB_8888, true)).thenReturn(secondCopy)
        val first = ZaintLayer(firstBitmap).apply { opacityPercentage = 35; isVisible = false }
        val second = ZaintLayer(secondBitmap).apply { opacityPercentage = 80 }
        val model = ZaintLayerModel()

        ZaintLayerStackDocumentLoad(listOf(first, second)).run(mock(Canvas::class.java), model)

        assertEquals(2, model.layerCount)
        assertSame(model.getLayerAt(0), model.currentLayer)
        assertNotSame(first, model.getLayerAt(0))
        assertSame(firstCopy, model.getLayerAt(0)?.bitmap)
        assertEquals(35, model.getLayerAt(0)?.opacityPercentage)
        assertEquals(false, model.getLayerAt(0)?.isVisible)
        assertSame(secondCopy, model.getLayerAt(1)?.bitmap)
        assertEquals(80, model.getLayerAt(1)?.opacityPercentage)
    }
}
