package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas
import org.junit.runner.RunWith
import de.zwegen.zpaint.model.ZaintLayerModel
import org.mockito.Mockito
import de.zwegen.zpaint.command.implementation.ZaintSelectLayerCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SelectLayerCommandTest {
    @Test
    fun testRunWhenNoLayerSelected() {
        val model = ZaintLayerModel()
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        model.addLayerAt(0, layer)

        val command = ZaintSelectLayerCommand(0)
        command.run(Mockito.mock(Canvas::class.java), model)

        Assert.assertThat(model.currentLayer, CoreMatchers.`is`(layer))
    }

    @Test
    fun testRunWhenOtherLayerSelected() {
        val model = ZaintLayerModel()
        val firstLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        model.addLayerAt(0, firstLayer)
        model.addLayerAt(1, secondLayer)
        model.currentLayer = firstLayer

        val command = ZaintSelectLayerCommand(1)
        command.run(Mockito.mock(Canvas::class.java), model)

        Assert.assertThat(model.currentLayer, CoreMatchers.`is`(secondLayer))
    }

    @Test
    fun ignoresASelectionOutsideTheCurrentDocument() {
        val model = ZaintLayerModel()
        val selectedLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        model.addLayerAt(0, selectedLayer)
        model.currentLayer = selectedLayer

        ZaintSelectLayerCommand(3).run(Mockito.mock(Canvas::class.java), model)

        Assert.assertThat(model.currentLayer, CoreMatchers.`is`(selectedLayer))
    }
}
