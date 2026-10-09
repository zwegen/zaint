package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.implementation.ZaintLayerStackDocumentLoad
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LoadLayerListCommandTest {

    private var canvas: Canvas = Mockito.mock(Canvas::class.java)
    private var layerModel: ZaintLayerContracts.Model = ZaintLayerModel()

    private lateinit var commandUnderTest: ZaintLayerStackDocumentLoad
    private lateinit var layerList: MutableList<ZaintLayerContracts.ZaintLayer>

    @Before
    fun setUp() {
        layerList = mutableListOf()
        for (i in 1..3) {
            val layer = ZaintLayer(Bitmap.createBitmap(2 * i, 2, Bitmap.Config.ARGB_8888))
            layerList.add(layer)
        }
        commandUnderTest = ZaintLayerStackDocumentLoad(layerList)
    }

    @Test
    fun testRunCopiesImage() {
        commandUnderTest.run(canvas, layerModel)
        Assert.assertTrue(layerModel.currentLayer!!.bitmap.sameAs(layerList[0].bitmap))
        Assert.assertTrue(layerModel.getLayerAt(1)!!.bitmap.sameAs(layerList[1].bitmap))
        Assert.assertTrue(layerModel.getLayerAt(2)!!.bitmap.sameAs(layerList[2].bitmap))
    }

    @Test
    fun testCheckLayersCount() {
        commandUnderTest.run(canvas, layerModel)
        Assert.assertThat(layerModel.layerCount, `is`(3))
    }

    @Test
    fun testRunSetsCurrentLayer() {
        commandUnderTest.run(canvas, layerModel)
        val currentLayer = layerModel.currentLayer
        Assert.assertThat(layerModel.getLayerAt(0), `is`(currentLayer))
    }
}
