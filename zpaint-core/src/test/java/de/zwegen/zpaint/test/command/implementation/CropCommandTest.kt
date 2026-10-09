package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.crop.CropRegion
import de.zwegen.zpaint.command.crop.CropSize
import de.zwegen.zpaint.command.crop.LayerCropper
import de.zwegen.zpaint.command.implementation.ZaintDocumentCrop
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class ZaintDocumentCropTest {
    @Test
    fun `applies a valid crop to every layer through the bitmap adapter`() {
        val model = ZaintLayerModel().apply {
            width = 10
            height = 12
            addLayerAt(0, ZaintLayer(mock(Bitmap::class.java)))
            addLayerAt(1, ZaintLayer(mock(Bitmap::class.java)))
        }
        val cropper = RecordingCropper()
        val command = ZaintDocumentCrop(2, 3, 7, 9, 100, cropper)

        command.run(mock(Canvas::class.java), model)

        assertEquals(2, cropper.calls.size)
        assertEquals(CropRegion(2, 3, 7, 9), cropper.calls.first().region)
        assertEquals(CropSize(6, 7), cropper.calls.first().targetSize)
        assertEquals(6, model.width)
        assertEquals(7, model.height)
    }

    private class RecordingCropper : LayerCropper {
        val calls = mutableListOf<Call>()

        override fun crop(layer: ZaintLayerContracts.ZaintLayer, region: CropRegion, targetSize: CropSize) {
            calls += Call(layer, region, targetSize)
        }
    }

    private data class Call(
        val layer: ZaintLayerContracts.ZaintLayer,
        val region: CropRegion,
        val targetSize: CropSize
    )
}
