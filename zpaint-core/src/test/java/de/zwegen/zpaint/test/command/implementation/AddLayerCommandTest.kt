package de.zwegen.zpaint.test.command.implementation

import org.junit.runner.RunWith
import org.mockito.Mock
import de.zwegen.zpaint.common.CommonFactory
import org.mockito.InjectMocks
import de.zwegen.zpaint.command.implementation.ZaintAddLayerCommand
import org.mockito.Mockito
import de.zwegen.zpaint.model.ZaintLayerModel
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class AddLayerCommandTest {
    @Mock
    private val commonFactory: CommonFactory? = null

    @Mock
    private val canvas: Canvas? = null

    @InjectMocks
    private val command: ZaintAddLayerCommand? = null

    @Test
    fun testSetUp() { Mockito.verifyNoInteractions(commonFactory) }

    @Test
    fun testAddOneLayer() {
        `when`(commonFactory!!.createBitmap(3, 5, Bitmap.Config.ARGB_8888)).thenReturn(mock(Bitmap::class.java))

        val layerModel = ZaintLayerModel()
        layerModel.width = 3
        layerModel.height = 5

        canvas?.let { command?.run(it, layerModel) }

        Mockito.verify(commonFactory)?.createBitmap(3, 5, Bitmap.Config.ARGB_8888)
        Assert.assertEquals(1, layerModel.layerCount.toLong())

        val currentLayer = layerModel.currentLayer
        Assert.assertEquals(currentLayer, layerModel.getLayerAt(0))
    }

    @Test
    fun testAddTwoLayersAddsToFront() {
        `when`(commonFactory!!.createBitmap(3, 5, Bitmap.Config.ARGB_8888)).thenReturn(mock(Bitmap::class.java))

        val layerModel = ZaintLayerModel()
        layerModel.width = 3
        layerModel.height = 5

        canvas?.let { command?.run(it, layerModel) }

        val firstLayer = layerModel.getLayerAt(0)

        canvas?.let { command?.run(it, layerModel) }

        Assert.assertEquals(2, layerModel.layerCount.toLong())

        val currentLayer = layerModel.currentLayer
        Assert.assertEquals(currentLayer, layerModel.getLayerAt(0))
        Assert.assertEquals(firstLayer, layerModel.getLayerAt(1))
    }

    @Test
    fun testAddMultipleLayersWillUseSameArguments() {
        `when`(commonFactory!!.createBitmap(7, 11, Bitmap.Config.ARGB_8888)).thenReturn(mock(Bitmap::class.java))
        val layerModel = ZaintLayerModel()

        layerModel.width = 7
        layerModel.height = 11

        if (canvas != null) { command?.run(canvas, layerModel) }
        if (canvas != null) { command?.run(canvas, layerModel) }
        if (canvas != null) { command?.run(canvas, layerModel) }
        if (canvas != null) { command?.run(canvas, layerModel) }

        Mockito.verify(
            commonFactory,
            Mockito.times(4)
        )?.createBitmap(7, 11, Bitmap.Config.ARGB_8888)
    }
}
