package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintLayerMirror
import de.zwegen.zpaint.command.implementation.ZaintMirrorDirection
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class FlipCommandTest {
    private lateinit var commandUnderTest: Command
    private var canvasUnderTest: Canvas? = null
    private lateinit var bitmapUnderTest: Bitmap
    private var layerModel: ZaintLayerModel? = null
    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()
        layerModel!!.width = INITIAL_WIDTH
        layerModel!!.height = INITIAL_HEIGHT
        val canvasBitmapUnderTest = Bitmap.createBitmap(INITIAL_WIDTH, INITIAL_HEIGHT, Bitmap.Config.ARGB_8888)
        canvasBitmapUnderTest.eraseColor(BITMAP_BASE_COLOR)
        bitmapUnderTest = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        val layerUnderTest = ZaintLayer(bitmapUnderTest)
        canvasUnderTest = Canvas()
        canvasUnderTest!!.setBitmap(canvasBitmapUnderTest)
        layerModel!!.addLayerAt(0, layerUnderTest)
        layerModel!!.currentLayer = layerUnderTest
    }

    @Test
    fun testVerticalFlip() {
        commandUnderTest = ZaintLayerMirror(ZaintMirrorDirection.VERTICAL)
        bitmapUnderTest!!.setPixel(0, INITIAL_HEIGHT / 2, PAINT_BASE_COLOR)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        val pixel = bitmapUnderTest!!.getPixel(INITIAL_WIDTH - 1, INITIAL_WIDTH / 2)
        Assert.assertEquals(PAINT_BASE_COLOR.toLong(), pixel.toLong())
    }

    @Test
    fun testHorizontalFlip() {
        commandUnderTest = ZaintLayerMirror(ZaintMirrorDirection.HORIZONTAL)
        bitmapUnderTest!!.setPixel(INITIAL_WIDTH / 2, 0, PAINT_BASE_COLOR)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        val pixel = bitmapUnderTest!!.getPixel(INITIAL_WIDTH / 2, INITIAL_WIDTH - 1)
        Assert.assertEquals(PAINT_BASE_COLOR.toLong(), pixel.toLong())
    }

    companion object {
        private const val BITMAP_BASE_COLOR = Color.GREEN
        private const val PAINT_BASE_COLOR = Color.BLUE
        private const val INITIAL_HEIGHT = 80
        private const val INITIAL_WIDTH = 80
    }
}
