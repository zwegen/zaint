package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.bitmap.CurrentLayerCanvasRotator
import de.zwegen.zpaint.command.bitmap.LayerRotator
import de.zwegen.zpaint.command.bitmap.LayerRotationBounds
import de.zwegen.zpaint.command.bitmap.QuarterTurnPlan
import de.zwegen.zpaint.command.implementation.ZaintDocumentRotation
import de.zwegen.zpaint.command.implementation.ZaintRotationDirection
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class ZaintDocumentRotationTest {
    @Test
    fun `document rotation transforms every layer and swaps dimensions`() {
        val model = modelWithTwoLayers()
        val rotator = RecordingRotator()

        ZaintDocumentRotation(ZaintRotationDirection.RIGHT, currentLayerOnly = false, layerRotator = rotator)
            .run(mock(Canvas::class.java), model)

        assertEquals(model.layers, rotator.documentLayers)
        assertEquals(90f, rotator.documentPlan?.angleDegrees)
        assertEquals(12, model.width)
        assertEquals(10, model.height)
    }

    @Test
    fun `current layer rotation expands document and keeps other layers unrotated`() {
        val model = modelWithTwoLayers()
        val rotator = RecordingRotator()
        val currentLayerRotator = RecordingCurrentLayerCanvasRotator()

        ZaintDocumentRotation(
            ZaintRotationDirection.LEFT,
            currentLayerOnly = true,
            layerRotator = rotator,
            currentLayerCanvasRotator = currentLayerRotator
        )
            .run(mock(Canvas::class.java), model)

        assertSame(model.currentLayer, currentLayerRotator.currentLayer)
        assertEquals(-90f, currentLayerRotator.angle)
        assertEquals(12, model.width)
        assertEquals(12, model.height)
    }

    private fun modelWithTwoLayers(): ZaintLayerModel = ZaintLayerModel().apply {
        width = 10
        height = 12
        val first = ZaintLayer(mock(Bitmap::class.java))
        addLayerAt(0, first)
        addLayerAt(1, ZaintLayer(mock(Bitmap::class.java)))
        currentLayer = first
    }

    private class RecordingRotator : LayerRotator {
        val documentLayers = mutableListOf<ZaintLayerContracts.ZaintLayer>()
        var documentPlan: QuarterTurnPlan? = null
        var currentLayer: ZaintLayerContracts.ZaintLayer? = null
        var currentPlan: QuarterTurnPlan? = null

        override fun rotateDocumentLayer(
            layer: ZaintLayerContracts.ZaintLayer,
            documentWidth: Int,
            documentHeight: Int,
            plan: QuarterTurnPlan
        ) {
            documentLayers += layer
            documentPlan = plan
        }

        override fun rotateCurrentLayer(layer: ZaintLayerContracts.ZaintLayer, plan: QuarterTurnPlan) {
            currentLayer = layer
            currentPlan = plan
        }
    }

    private class RecordingCurrentLayerCanvasRotator : CurrentLayerCanvasRotator {
        var currentLayer: ZaintLayerContracts.ZaintLayer? = null
        var angle: Float? = null

        override fun rotateAndExpandCanvas(layerModel: ZaintLayerContracts.Model, angleDegrees: Float) {
            currentLayer = layerModel.currentLayer
            angle = angleDegrees
            LayerRotationBounds.expandedSize(layerModel.width, layerModel.height, angleDegrees).let { size ->
                layerModel.width = size.width
                layerModel.height = size.height
            }
        }
    }
}
