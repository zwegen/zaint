package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintDocumentCrop
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CropCommandTest {
    private var resizeCoordinateXLeft = 0
    private var resizeCoordinateYTop = 0
    private var resizeCoordinateXRight = 0
    private var resizeCoordinateYBottom = 0
    private var maximumBitmapResolution = 0
    private lateinit var commandUnderTest: Command
    private lateinit var canvasUnderTest: Canvas
    private lateinit var bitmapUnderTest: Bitmap
    private lateinit var layerUnderTest: ZaintLayer
    private lateinit var layerModel: ZaintLayerModel
    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()
        layerModel!!.width = INITIAL_WIDTH
        layerModel!!.height = INITIAL_HEIGHT
        val canvasBitmapUnderTest = Bitmap.createBitmap(INITIAL_WIDTH, INITIAL_HEIGHT, Bitmap.Config.ARGB_8888)
        canvasBitmapUnderTest.eraseColor(BITMAP_BASE_COLOR)
        bitmapUnderTest = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        layerUnderTest = ZaintLayer(bitmapUnderTest)
        canvasUnderTest = Canvas()
        canvasUnderTest!!.setBitmap(canvasBitmapUnderTest)
        layerModel!!.addLayerAt(0, layerUnderTest!!)
        layerModel!!.currentLayer = layerUnderTest
        resizeCoordinateXLeft = 0
        resizeCoordinateYTop = 0
        resizeCoordinateXRight = bitmapUnderTest.width - 1
        resizeCoordinateYBottom = bitmapUnderTest.height - 1
        maximumBitmapResolution = bitmapUnderTest.width * bitmapUnderTest.height * MAXIMUM_BITMAP_RESOLUTION_FACTOR
        commandUnderTest = ZaintDocumentCrop(resizeCoordinateXLeft, resizeCoordinateYTop,
                resizeCoordinateXRight, resizeCoordinateYBottom, maximumBitmapResolution)
    }

    @Test
    fun testIfBitmapIsCropped() {
        val widthOriginal = bitmapUnderTest!!.width
        val heightOriginal = bitmapUnderTest!!.height
        resizeCoordinateXLeft = 1
        resizeCoordinateYTop = 1
        resizeCoordinateXRight = bitmapUnderTest!!.width - 2
        resizeCoordinateYBottom = bitmapUnderTest!!.height - 2
        commandUnderTest = ZaintDocumentCrop(resizeCoordinateXLeft, resizeCoordinateYTop,
                resizeCoordinateXRight, resizeCoordinateYBottom, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        val croppedBitmap = layerUnderTest!!.bitmap
        Assert.assertEquals("Cropping failed, width not correct ", (widthOriginal - resizeCoordinateXLeft - (widthOriginal - (resizeCoordinateXRight + 1))).toLong(), croppedBitmap.width.toLong())
        Assert.assertEquals("Cropping failed, height not correct ", (heightOriginal - resizeCoordinateYTop - (widthOriginal - (resizeCoordinateYBottom + 1))).toLong(), croppedBitmap.height.toLong())
        croppedBitmap.recycle()
    }

    @Test
    fun testIfBitmapIsEnlarged() {
        val widthOriginal = bitmapUnderTest!!.width
        val heightOriginal = bitmapUnderTest!!.height
        resizeCoordinateXLeft = -1
        resizeCoordinateYTop = -1
        resizeCoordinateXRight = bitmapUnderTest!!.width
        resizeCoordinateYBottom = bitmapUnderTest!!.height
        commandUnderTest = ZaintDocumentCrop(resizeCoordinateXLeft, resizeCoordinateYTop,
                resizeCoordinateXRight, resizeCoordinateYBottom, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        val enlargedBitmap = layerUnderTest!!.bitmap
        Assert.assertEquals("Enlarging failed, width not correct ", (widthOriginal - resizeCoordinateXLeft - (widthOriginal - (resizeCoordinateXRight + 1))).toLong(), enlargedBitmap.width.toLong())
        Assert.assertEquals("Enlarging failed, height not correct ", (heightOriginal - resizeCoordinateYTop - (widthOriginal - (resizeCoordinateYBottom + 1))).toLong(), enlargedBitmap.height.toLong())
        enlargedBitmap.recycle()
    }

    @Test
    fun testIfBitmapIsShifted() {
        val widthOriginal = bitmapUnderTest!!.width
        val heightOriginal = bitmapUnderTest!!.height
        resizeCoordinateXLeft = bitmapUnderTest!!.width / 2 - 1
        resizeCoordinateYTop = bitmapUnderTest!!.height / 2 - 1
        resizeCoordinateXRight = resizeCoordinateXLeft + bitmapUnderTest!!.width - 1
        resizeCoordinateYBottom = resizeCoordinateYTop + bitmapUnderTest!!.height - 1
        commandUnderTest = ZaintDocumentCrop(resizeCoordinateXLeft, resizeCoordinateYTop,
                resizeCoordinateXRight, resizeCoordinateYBottom, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        val enlargedBitmap = layerUnderTest!!.bitmap
        Assert.assertEquals("Enlarging failed, width not correct ", widthOriginal.toLong(), enlargedBitmap.width.toLong())
        Assert.assertEquals("Enlarging failed, height not correct ", heightOriginal.toLong(), enlargedBitmap.height.toLong())
        enlargedBitmap.recycle()
    }

    @Test
    fun testIfMaximumResolutionIsRespected() {
        val widthOriginal = bitmapUnderTest!!.width
        val heightOriginal = bitmapUnderTest!!.height
        commandUnderTest = ZaintDocumentCrop(0, 0, widthOriginal * 2, heightOriginal * 2, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertEquals("Width should not have changed", widthOriginal.toLong(), layerUnderTest!!.bitmap.width.toLong())
        Assert.assertEquals("Height should not have changed", heightOriginal.toLong(), layerUnderTest!!.bitmap.height.toLong())
    }

    @Test
    fun testIfBitmapIsNotResizedWithInvalidBounds() {
        val originalBitmap = layerUnderTest!!.bitmap
        commandUnderTest = ZaintDocumentCrop(bitmapUnderTest!!.width, 0, bitmapUnderTest!!.width,
                0, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertTrue("bitmap must not change if X left is larger than bitmap scope", originalBitmap.sameAs(layerUnderTest!!.bitmap))
        commandUnderTest = ZaintDocumentCrop(-1, 0, -1, 0, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertTrue("bitmap must not change if X right is smaller than bitmap scope", originalBitmap.sameAs(layerUnderTest!!.bitmap))
        commandUnderTest = ZaintDocumentCrop(0, bitmapUnderTest!!.height, 0,
                bitmapUnderTest!!.height, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertTrue("bitmap must not change if Y top is larger than bitmap scope", originalBitmap.sameAs(layerUnderTest!!.bitmap))
        commandUnderTest = ZaintDocumentCrop(0, -1, 0, -1, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertTrue("bitmap must not change if Y bottom is smaller than bitmap scope", originalBitmap.sameAs(layerUnderTest!!.bitmap))
        commandUnderTest = ZaintDocumentCrop(1, 0, 0, 0, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertTrue("bitmap must not change with widthXRight < widthXLeft bound", originalBitmap.sameAs(layerUnderTest!!.bitmap))
        commandUnderTest = ZaintDocumentCrop(0, 1, 0, 0, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertTrue("bitmap must not change with widthYBottom < widthYTop bound", originalBitmap.sameAs(layerUnderTest!!.bitmap))
        commandUnderTest = ZaintDocumentCrop(0, 0, bitmapUnderTest!!.width - 1,
                bitmapUnderTest!!.height - 1, maximumBitmapResolution)
        commandUnderTest.run(canvasUnderTest!!, layerModel!!)
        Assert.assertTrue("bitmap must not change because bounds are the same as original bitmap", originalBitmap.sameAs(layerUnderTest!!.bitmap))
    }

    companion object {
        private const val MAXIMUM_BITMAP_RESOLUTION_FACTOR = 4
        private const val BITMAP_BASE_COLOR = Color.GREEN
        private const val INITIAL_HEIGHT = 80
        private const val INITIAL_WIDTH = 80
    }
}
