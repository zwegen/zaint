package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.implementation.ZaintLayerOpacityChange
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Test
import org.mockito.Mockito

class LayerOpacityCommandTest {
    @Test
    fun appliesOpacityToTheRequestedLayer() {
        val model = ZaintLayerModel()
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        model.addLayerAt(0, layer)

        ZaintLayerOpacityChange(layerIndex = 0, opacity = 42)
            .run(Mockito.mock(Canvas::class.java), model)

        Mockito.verify(layer).opacityPercentage = 42
    }

    @Test
    fun ignoresAnUnavailableLayer() {
        val model = ZaintLayerModel()

        ZaintLayerOpacityChange(layerIndex = 0, opacity = 42)
            .run(Mockito.mock(Canvas::class.java), model)
    }
}
