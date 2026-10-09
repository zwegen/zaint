package de.zwegen.zpaint.test.command.implementation

import org.junit.runner.RunWith
import org.mockito.Mock
import android.graphics.Bitmap
import android.graphics.Canvas
import org.mockito.InjectMocks
import de.zwegen.zpaint.command.implementation.ZaintBitmapDocumentLoad
import de.zwegen.zpaint.contract.ZaintLayerContracts
import org.junit.Before
import de.zwegen.zpaint.model.ZaintLayerModel
import org.mockito.Mockito
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ZaintBitmapDocumentLoadTest {
    @Mock
    var bitmap: Bitmap? = null

    @Mock
    var canvas: Canvas? = null
    var layerModel: ZaintLayerContracts.Model? = null

    @InjectMocks
    var command: ZaintBitmapDocumentLoad? = null

    @Before
    fun setUp() { layerModel = ZaintLayerModel() }

    @Test
    fun testSetUp() { Mockito.verifyNoInteractions(bitmap) }

    @Test
    fun testRunCopiesImage() {
        val copy = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap?.copy(Bitmap.Config.ARGB_8888, true)).thenReturn(copy)

        layerModel?.let { canvas?.let { it1 -> command?.run(it1, it) } }

        val currentLayer = layerModel?.currentLayer
        if (currentLayer != null) { Assert.assertThat(currentLayer.bitmap, CoreMatchers.`is`(copy)) }
    }

    @Test
    fun testRunAddsOneLayer() {
        val clone = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap?.copy(Bitmap.Config.ARGB_8888, true)).thenReturn(clone)

        canvas?.let { command?.run(it, layerModel!!) }

        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(1))
    }

    @Test
    fun testRunSetsCurrentLayer() {
        val clone = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap?.copy(Bitmap.Config.ARGB_8888, true)).thenReturn(clone)

        canvas?.let { layerModel?.let { it1 -> command?.run(it, it1) } }

        val currentLayer = layerModel?.currentLayer
        Assert.assertThat(layerModel?.getLayerAt(0), CoreMatchers.`is`(currentLayer))
    }
}
