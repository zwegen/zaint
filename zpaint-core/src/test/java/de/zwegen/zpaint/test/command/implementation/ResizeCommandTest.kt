package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.bitmap.LayerResizer
import de.zwegen.zpaint.command.bitmap.ResizeTarget
import de.zwegen.zpaint.command.implementation.ZaintDocumentResize
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class ZaintDocumentResizeTest {
    @Test
    fun `resizes every layer through the bitmap adapter and updates document size`() {
        val model = ZaintLayerModel().apply {
            addLayerAt(0, ZaintLayer(mock(Bitmap::class.java)))
            addLayerAt(1, ZaintLayer(mock(Bitmap::class.java)))
        }
        val resizer = RecordingLayerResizer()

        ZaintDocumentResize(640, 480, resizer).run(mock(Canvas::class.java), model)

        assertEquals(model.layers, resizer.layers)
        assertEquals(ResizeTarget(640, 480), resizer.target)
        assertEquals(640, model.width)
        assertEquals(480, model.height)
    }

    private class RecordingLayerResizer : LayerResizer {
        val layers = mutableListOf<ZaintLayerContracts.ZaintLayer>()
        var target: ResizeTarget? = null

        override fun resize(layer: ZaintLayerContracts.ZaintLayer, target: ResizeTarget) {
            layers += layer
            this.target = target
        }
    }
}
