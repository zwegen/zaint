package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintLayerMerge
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MergeLayersCommandTest {
    private lateinit var commandUnderTest: Command
    private lateinit var layerModel: ZaintLayerModel
    private lateinit var canvasUnderTest: Canvas
    private lateinit var canvasBitmapUnderTest: Bitmap
    private lateinit var bitmapUnderTest1: Bitmap
    private lateinit var bitmapUnderTest2: Bitmap
    private lateinit var bitmapUnderTest3: Bitmap

    companion object {
        private const val PAINT_BASE_COLOR = Color.BLUE
        private const val INITIAL_HEIGHT = 80
        private const val INITIAL_WIDTH = 80
    }

    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()
        layerModel.width = INITIAL_WIDTH
        layerModel.height = INITIAL_HEIGHT
        canvasBitmapUnderTest = Bitmap.createBitmap(INITIAL_WIDTH, INITIAL_HEIGHT, Bitmap.Config.ARGB_8888)
        bitmapUnderTest1 = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        bitmapUnderTest2 = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        bitmapUnderTest3 = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)

        val layerUnderTest1 = ZaintLayer(bitmapUnderTest1)
        val layerUnderTest2 = ZaintLayer(bitmapUnderTest2)
        val layerUnderTest3 = ZaintLayer(bitmapUnderTest3)

        canvasUnderTest = Canvas()
        canvasUnderTest.setBitmap(canvasBitmapUnderTest)
        bitmapUnderTest1.setPixel(0, 0, PAINT_BASE_COLOR)
        bitmapUnderTest2.setPixel(5, 5, PAINT_BASE_COLOR)
        bitmapUnderTest3.setPixel(8, 8, PAINT_BASE_COLOR)
        layerModel.addLayerAt(0, layerUnderTest1)
        layerModel.addLayerAt(0, layerUnderTest2)
        layerModel.addLayerAt(0, layerUnderTest3)
        layerModel.currentLayer = layerUnderTest1
        commandUnderTest = ZaintLayerMerge(0, 1)
    }

    @Test
    fun testRun() {
        commandUnderTest.run(canvasUnderTest, layerModel)
        Assert.assertEquals(layerModel.getLayerAt(0)!!.bitmap!!.getPixel(5, 5), PAINT_BASE_COLOR)
        Assert.assertEquals(layerModel.getLayerAt(0)!!.bitmap!!.getPixel(8, 8), PAINT_BASE_COLOR)
        Assert.assertEquals(layerModel.getLayerAt(0)!!.bitmap!!.getPixel(3, 3), 0)
        Assert.assertEquals(layerModel.getLayerAt(0)!!.bitmap!!.getPixel(0, 0), 0)
    }

    @Test
    fun testCheckLayersCount() {
        commandUnderTest.run(canvasUnderTest, layerModel)
        Assert.assertThat(layerModel.layerCount, CoreMatchers.`is`(2))
        commandUnderTest.run(canvasUnderTest, layerModel)
        Assert.assertThat(layerModel.layerCount, CoreMatchers.`is`(1))
    }

    @Test
    fun testRunMergeSeparatedLayers() {
        commandUnderTest = ZaintLayerMerge(2, 0)
        commandUnderTest.run(canvasUnderTest, layerModel)
        Assert.assertEquals(layerModel.currentLayer!!.bitmap!!.getPixel(0, 0), PAINT_BASE_COLOR)
        Assert.assertEquals(layerModel.currentLayer!!.bitmap!!.getPixel(8, 8), PAINT_BASE_COLOR)
        Assert.assertEquals(layerModel.currentLayer!!.bitmap!!.getPixel(3, 3), 0)
        Assert.assertEquals(layerModel.currentLayer!!.bitmap!!.getPixel(5, 5), 0)
        Assert.assertEquals(layerModel.getLayerAt(1)!!.bitmap!!.getPixel(5, 5), PAINT_BASE_COLOR)
    }

    @Test
    fun testRunWithSameLayerKeepsDocumentUntouched() {
        commandUnderTest = ZaintLayerMerge(1, 1)

        commandUnderTest.run(canvasUnderTest, layerModel)

        Assert.assertThat(layerModel.layerCount, CoreMatchers.`is`(3))
        Assert.assertEquals(layerModel.getLayerAt(1)!!.bitmap!!.getPixel(5, 5), PAINT_BASE_COLOR)
    }
}
