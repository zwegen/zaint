package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintAddLayerCommand
import de.zwegen.zpaint.command.implementation.ZaintDocumentResize
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ResizeCommandTest {
    private var canvasUnderTest: Canvas? = null
    private var layerModel: ZaintLayerModel? = null
    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()
        layerModel!!.width = INITIAL_WIDTH
        layerModel!!.height = INITIAL_HEIGHT
        val bitmapUnderTest = Bitmap.createBitmap(INITIAL_WIDTH, INITIAL_HEIGHT, Bitmap.Config.ARGB_8888)
        val layerUnderTest = ZaintLayer(bitmapUnderTest)
        canvasUnderTest = Canvas()
        canvasUnderTest!!.setBitmap(bitmapUnderTest)
        layerModel!!.addLayerAt(0, layerUnderTest)
        layerModel!!.currentLayer = layerUnderTest
    }

    @Test
    fun testResizeCommand() {
        val commandUnderTest: Command = ZaintDocumentResize(NEW_WIDTH, NEW_HEIGHT)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertEquals(40, layerModel!!.height.toLong())
        Assert.assertEquals(40, layerModel!!.width.toLong())
    }

    @Test
    fun testBitmapKeepsDrawings() {
        layerModel!!.currentLayer!!.bitmap.setPixel(0, 0, Color.BLACK)
        var currentPixel = layerModel!!.currentLayer!!.bitmap.getPixel(0, 0)
        Assert.assertNotEquals(currentPixel.toLong(), 0)
        val commandUnderTest: Command = ZaintDocumentResize(NEW_WIDTH, NEW_HEIGHT)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        currentPixel = layerModel!!.currentLayer!!.bitmap.getPixel(0, 0)
        Assert.assertNotEquals(currentPixel.toLong(), 0)
    }

    @Test
    fun testLayerStayInSameOrderOnResize() {
        layerModel!!.getLayerAt(0)!!.bitmap.eraseColor(Color.GREEN)
        var addLayerCommand: Command = ZaintAddLayerCommand(CommonFactory())
        addLayerCommand.run(canvasUnderTest!!, layerModel!!)
        layerModel!!.getLayerAt(0)!!.bitmap.eraseColor(Color.YELLOW)
        addLayerCommand = ZaintAddLayerCommand(CommonFactory())
        addLayerCommand.run(canvasUnderTest!!, layerModel!!)
        layerModel!!.getLayerAt(0)!!.bitmap.eraseColor(Color.BLUE)
        val commandUnderTest: Command = ZaintDocumentResize(NEW_WIDTH, NEW_HEIGHT)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertEquals(layerModel!!.getLayerAt(2)!!.bitmap.getPixel(0, 0).toLong(), Color.GREEN.toLong())
        Assert.assertEquals(layerModel!!.getLayerAt(1)!!.bitmap.getPixel(0, 0).toLong(), Color.YELLOW.toLong())
        Assert.assertEquals(layerModel!!.getLayerAt(0)!!.bitmap.getPixel(0, 0).toLong(), Color.BLUE.toLong())
    }

    @Test
    fun testAllLayersAreResized() {
        var addLayerCommand: Command = ZaintAddLayerCommand(CommonFactory())
        addLayerCommand.run(canvasUnderTest!!, layerModel!!)
        addLayerCommand = ZaintAddLayerCommand(CommonFactory())
        addLayerCommand.run(canvasUnderTest!!, layerModel!!)
        val commandUnderTest: Command = ZaintDocumentResize(NEW_WIDTH, NEW_HEIGHT)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertEquals(layerModel!!.getLayerAt(2)!!.bitmap.height.toLong(), NEW_HEIGHT.toLong())
        Assert.assertEquals(layerModel!!.getLayerAt(2)!!.bitmap.width.toLong(), NEW_WIDTH.toLong())
        Assert.assertEquals(layerModel!!.getLayerAt(1)!!.bitmap.height.toLong(), NEW_HEIGHT.toLong())
        Assert.assertEquals(layerModel!!.getLayerAt(1)!!.bitmap.width.toLong(), NEW_WIDTH.toLong())
        Assert.assertEquals(layerModel!!.getLayerAt(0)!!.bitmap.height.toLong(), NEW_HEIGHT.toLong())
        Assert.assertEquals(layerModel!!.getLayerAt(0)!!.bitmap.width.toLong(), NEW_WIDTH.toLong())
    }

    companion object {
        private const val INITIAL_HEIGHT = 80
        private const val INITIAL_WIDTH = 80
        private const val NEW_WIDTH = 40
        private const val NEW_HEIGHT = 40
    }
}
