package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas

import org.junit.runner.RunWith
import org.mockito.Mock
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.command.implementation.ZaintLayerReorder
import de.zwegen.zpaint.contract.ZaintLayerContracts
import org.junit.Before
import org.mockito.Mockito
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ReorderLayersCommandTest {
    @Mock
    private val firstLayer: ZaintLayerContracts.ZaintLayer? = null

    @Mock
    private val secondLayer: ZaintLayerContracts.ZaintLayer? = null

    @Mock
    private val thirdLayer: ZaintLayerContracts.ZaintLayer? = null
    private var layerModel: ZaintLayerModel? = null
    private var command: ZaintLayerReorder? = null

    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()

        if (firstLayer != null) { layerModel?.addLayerAt(0, firstLayer) }
        if (secondLayer != null) { layerModel?.addLayerAt(1, secondLayer) }
        if (thirdLayer != null) { layerModel?.addLayerAt(2, thirdLayer) }
    }

    @Test
    fun testRunReorderToBack() {
        command = ZaintLayerReorder(0, 2)

        layerModel?.let { command?.run(Mockito.mock(Canvas::class.java), it) }

        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(secondLayer))
        Assert.assertThat(layerModel?.getLayerAt(1), CoreMatchers.`is`(thirdLayer))
        Assert.assertThat(layerModel?.getLayerAt(2), CoreMatchers.`is`(firstLayer))
        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(3))
    }

    @Test
    fun testRunReorderToFront() {
        command = ZaintLayerReorder(2, 0)

        layerModel?.let { command?.run(Mockito.mock(Canvas::class.java), it) }

        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(thirdLayer))
        Assert.assertThat(layerModel?.getLayerAt(1), CoreMatchers.`is`(firstLayer))
        Assert.assertThat(layerModel?.getLayerAt(2), CoreMatchers.`is`(secondLayer))
        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(3))
    }

    @Test
    fun testRunDoesNotAffectCurrentLayer() {
        command = ZaintLayerReorder(2, 0)

        layerModel?.let { command?.run(Mockito.mock(Canvas::class.java), it) }

        Assert.assertNull(layerModel?.currentLayer)
    }

    @Test
    fun testRunKeepsSourceLayerWhenDestinationIsInvalid() {
        command = ZaintLayerReorder(0, 99)

        layerModel?.let { command?.run(Mockito.mock(Canvas::class.java), it) }

        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(3))
        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(firstLayer))
    }
}
