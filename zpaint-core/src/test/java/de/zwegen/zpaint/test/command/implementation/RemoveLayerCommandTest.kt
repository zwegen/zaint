package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas
import org.junit.runner.RunWith
import org.mockito.Mock
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.command.implementation.ZaintRemoveLayerCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts
import org.junit.Before
import org.mockito.Mockito
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class RemoveLayerCommandTest {
    @Mock
    private val firstLayer: ZaintLayerContracts.ZaintLayer? = null

    @Mock
    private val secondLayer: ZaintLayerContracts.ZaintLayer? = null
    private var layerModel: ZaintLayerModel? = null
    private var command: ZaintRemoveLayerCommand? = null

    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()

        if (firstLayer != null) { layerModel?.addLayerAt(0, firstLayer) }
        if (secondLayer != null) { layerModel?.addLayerAt(1, secondLayer) }
    }

    @Test
    fun testRunRemovesOneLayer() {
        command = ZaintRemoveLayerCommand(1)

        layerModel?.let { command?.run(Mockito.mock(Canvas::class.java), it) }

        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(1))
        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(firstLayer))
    }

    @Test
    fun testRunSetsCurrentLayer() {
        command = ZaintRemoveLayerCommand(0)

        layerModel?.let { command?.run(Mockito.mock(Canvas::class.java), it) }

        val currentLayer = layerModel?.currentLayer
        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(currentLayer))
        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(secondLayer))
    }
}
