package de.zwegen.zpaint.test.model

import de.zwegen.zpaint.contract.ZaintLayerContracts
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import de.zwegen.zpaint.model.ZaintLayerModel
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LayerModelTest {
    @InjectMocks
    private val layerModel: ZaintLayerModel? = null

    @Test
    fun testSetUp() {
        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(0))
        Assert.assertThat(layerModel?.width, CoreMatchers.`is`(0))
        Assert.assertThat(layerModel?.height, CoreMatchers.`is`(0))
        Assert.assertNotNull(layerModel?.layers)
        Assert.assertNull(layerModel?.currentLayer)

        val iterator: ListIterator<ZaintLayerContracts.ZaintLayer>? = layerModel?.listIterator(0)

        Assert.assertNotNull(iterator)
        iterator?.hasNext()?.let { Assert.assertFalse(it) }
    }

    @Test
    fun testGetCurrentLayer() {
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel?.currentLayer = layer

        Assert.assertThat(layerModel?.currentLayer, CoreMatchers.`is`(layer))
        Mockito.verifyNoInteractions(layer)
    }

    @Test
    fun testGetWidth() {
        layerModel?.width = 3

        Assert.assertThat(layerModel?.width, CoreMatchers.`is`(3))
    }

    @Test
    fun testGetHeight() {
        layerModel?.height = 5

        Assert.assertThat(layerModel?.height, CoreMatchers.`is`(5))
    }

    @Test
    fun testReset() {
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        layerModel?.addLayerAt(0, layer)
        layerModel?.reset()

        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(0))
        Mockito.verifyNoInteractions(layer)
    }

    @Test
    fun testGetLayerCount() {
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel?.addLayerAt(0, layer)

        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(1))
        Mockito.verifyNoInteractions(layer)
    }

    @Test
    fun testGetLayerAt() {
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel?.addLayerAt(0, layer)

        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(layer))
        Mockito.verifyNoInteractions(layer)
    }

    @Test
    fun testGetLayerIndexOf() {
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel?.addLayerAt(0, layer)

        Assert.assertThat(layerModel?.getLayerIndexOf(layer), CoreMatchers.`is`(0))
        Mockito.verifyNoInteractions(layer)
    }

    @Test
    fun testListIterator() {
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel?.addLayerAt(0, layer)

        val iterator: ListIterator<ZaintLayerContracts.ZaintLayer>? = layerModel?.listIterator(0)

        Assert.assertThat(iterator?.next(), CoreMatchers.`is`(layer))
        Mockito.verifyNoInteractions(layer)
    }

    @Test
    fun testSetLayerAt() {
        val firstLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.setLayerAt(0, secondLayer)

        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(1))
        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(secondLayer))
        Mockito.verifyNoInteractions(firstLayer, secondLayer)
    }

    @Test
    fun testRemoveLayerAt() {
        val firstLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(0, secondLayer)
        layerModel?.removeLayerAt(0)

        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(1))
        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(firstLayer))
        Mockito.verifyNoInteractions(firstLayer, secondLayer)
    }
}
