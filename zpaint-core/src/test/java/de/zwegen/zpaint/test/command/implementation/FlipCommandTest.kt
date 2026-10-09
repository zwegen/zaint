package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.bitmap.LayerMirror
import de.zwegen.zpaint.command.bitmap.MirrorPlan
import de.zwegen.zpaint.command.implementation.ZaintLayerMirror
import de.zwegen.zpaint.command.implementation.ZaintMirrorDirection
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class ZaintLayerMirrorTest {
    @Test
    fun `mirrors only the selected layer when requested`() {
        val model = modelWithTwoLayers()
        val mirror = RecordingLayerMirror()

        ZaintLayerMirror(ZaintMirrorDirection.HORIZONTAL, currentLayerOnly = true, layerMirror = mirror)
            .run(mock(Canvas::class.java), model)

        assertEquals(1, mirror.calls.size)
        assertSame(model.currentLayer, mirror.calls.single().layer)
        assertEquals(MirrorPlan(1f, -1f, 0f, 12f), mirror.calls.single().plan)
    }

    @Test
    fun `mirrors every layer when document mode is requested`() {
        val model = modelWithTwoLayers()
        val mirror = RecordingLayerMirror()

        ZaintLayerMirror(ZaintMirrorDirection.VERTICAL, currentLayerOnly = false, layerMirror = mirror)
            .run(mock(Canvas::class.java), model)

        assertEquals(model.layers, mirror.calls.map { it.layer })
        assertEquals(MirrorPlan(-1f, 1f, 10f, 0f), mirror.calls.first().plan)
    }

    private fun modelWithTwoLayers(): ZaintLayerModel = ZaintLayerModel().apply {
        width = 10
        height = 12
        val first = ZaintLayer(mock(Bitmap::class.java))
        addLayerAt(0, first)
        addLayerAt(1, ZaintLayer(mock(Bitmap::class.java)))
        currentLayer = first
    }

    private class RecordingLayerMirror : LayerMirror {
        val calls = mutableListOf<Call>()

        override fun mirror(layer: ZaintLayerContracts.ZaintLayer, plan: MirrorPlan) {
            calls += Call(layer, plan)
        }
    }

    private data class Call(val layer: ZaintLayerContracts.ZaintLayer, val plan: MirrorPlan)
}
