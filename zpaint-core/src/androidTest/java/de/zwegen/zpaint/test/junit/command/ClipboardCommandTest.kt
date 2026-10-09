package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import de.zwegen.zpaint.ZPaintApplication.Companion.cacheDir
import de.zwegen.zpaint.command.implementation.ZaintClipboardStamp
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.test.utils.ZPaintAsserts
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

class ClipboardCommandTest {
    private lateinit var stampBitmapUnderTest: Bitmap
    private var commandUnderTest: ZaintClipboardStamp? = null
    private var pointUnderTest: PointF? = null
    private lateinit var canvasUnderTest: Canvas
    private lateinit var canvasBitmapUnderTest: Bitmap
    private var layerModel: ZaintLayerModel? = null
    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()
        layerModel!!.width = INITIAL_WIDTH
        layerModel!!.height = INITIAL_HEIGHT
        canvasBitmapUnderTest = Bitmap.createBitmap(INITIAL_WIDTH, INITIAL_HEIGHT, Bitmap.Config.ARGB_8888)
        canvasBitmapUnderTest.eraseColor(BITMAP_BASE_COLOR)
        val bitmapUnderTest = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        val layerUnderTest = ZaintLayer(bitmapUnderTest)
        canvasUnderTest = Canvas()
        canvasUnderTest!!.setBitmap(canvasBitmapUnderTest)
        pointUnderTest = PointF((INITIAL_WIDTH / 2).toFloat(), (INITIAL_HEIGHT / 2).toFloat())
        layerModel!!.addLayerAt(0, layerUnderTest)
        layerModel!!.currentLayer = layerUnderTest
        cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        stampBitmapUnderTest = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        stampBitmapUnderTest.eraseColor(BITMAP_REPLACE_COLOR)
        commandUnderTest = ZaintClipboardStamp(stampBitmapUnderTest, Point(canvasBitmapUnderTest.getWidth() / 2,
                canvasBitmapUnderTest.getHeight() / 2), canvasBitmapUnderTest.getWidth().toFloat(),
                                            canvasBitmapUnderTest.getHeight().toFloat(), 0f)
    }

    @Test
    fun testRun() {
        val layer = ZaintLayer(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val model = ZaintLayerModel()
        model.addLayerAt(0, layer)
        model.currentLayer = layer
        commandUnderTest!!.run(canvasUnderTest!!, model)
        ZPaintAsserts.assertBitmapEquals(stampBitmapUnderTest, canvasBitmapUnderTest)
        Assert.assertNull("Stamp bitmap not recycled.", commandUnderTest!!.bitmap)
        Assert.assertNotNull("Bitmap not stored", commandUnderTest!!.fileToStoredBitmap)
        val secondLayer = ZaintLayer(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        val secondModel = ZaintLayerModel()
        secondModel.addLayerAt(0, secondLayer)
        secondModel.currentLayer = secondLayer
        commandUnderTest!!.run(canvasUnderTest!!, secondModel)
        ZPaintAsserts.assertBitmapEquals(stampBitmapUnderTest, canvasBitmapUnderTest)
    }

    @Test
    fun testRunRotateStamp() {
        stampBitmapUnderTest!!.setPixel(0, 0, Color.GREEN)
        commandUnderTest = ZaintClipboardStamp(stampBitmapUnderTest!!, Point(pointUnderTest!!.x.toInt(), pointUnderTest!!.y.toInt()), canvasBitmapUnderTest!!.width.toFloat(), canvasBitmapUnderTest!!.height.toFloat(), 180f)
        commandUnderTest!!.run(canvasUnderTest!!, ZaintLayerModel())
        stampBitmapUnderTest!!.setPixel(0, 0, Color.CYAN)
        stampBitmapUnderTest!!.setPixel(stampBitmapUnderTest!!.width - 1, stampBitmapUnderTest!!.height - 1,
                Color.GREEN)
        ZPaintAsserts.assertBitmapEquals(stampBitmapUnderTest, canvasBitmapUnderTest)
        Assert.assertNull("Stamp bitmap not recycled.", commandUnderTest!!.bitmap)
        Assert.assertNotNull("Bitmap not stored", commandUnderTest!!.fileToStoredBitmap)
    }

    @Test
    @Throws(Exception::class)
    fun testFreeResources() {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val storedBitmap = File(cacheDir, "test")
        Assert.assertFalse(storedBitmap.exists())
        commandUnderTest!!.fileToStoredBitmap = storedBitmap
        commandUnderTest!!.freeResources()
        Assert.assertNull(commandUnderTest!!.bitmap)
        val restoredBitmap = commandUnderTest!!.fileToStoredBitmap
        Assert.assertFalse("bitmap not deleted", restoredBitmap!!.exists())
        if (restoredBitmap.exists()) {
            Assert.assertTrue(restoredBitmap.delete())
        }
        Assert.assertTrue(storedBitmap.createNewFile())
        Assert.assertTrue(storedBitmap.exists())
        commandUnderTest!!.freeResources()
        Assert.assertFalse(storedBitmap.exists())
        Assert.assertNull(commandUnderTest!!.bitmap)
    }

    @Test
    fun testStoreBitmap() {
        var storedBitmap: File? = null
        try {
            val bitmapCopy = canvasBitmapUnderTest!!.copy(canvasBitmapUnderTest!!.config, canvasBitmapUnderTest!!.isMutable)
            commandUnderTest!!.storeBitmap(bitmapCopy, bitmapCopy.width.toFloat(), bitmapCopy.height.toFloat())
            storedBitmap = commandUnderTest!!.fileToStoredBitmap
            Assert.assertNotNull(storedBitmap)
            Assert.assertNotNull(storedBitmap!!.absolutePath)
            val restoredBitmap = BitmapFactory.decodeFile(storedBitmap.absolutePath)
            ZPaintAsserts.assertBitmapEquals("Loaded file doesn't match saved file.", restoredBitmap, bitmapCopy)
        } finally {
            Assert.assertNotNull("Failed to delete the stored bitmap(0)", storedBitmap)
            Assert.assertTrue("Failed to delete the stored bitmap(1)", storedBitmap!!.delete())
        }
    }

    companion object {
        private const val BITMAP_BASE_COLOR = Color.GREEN
        private const val BITMAP_REPLACE_COLOR = Color.CYAN
        private const val INITIAL_HEIGHT = 80
        private const val INITIAL_WIDTH = 80
    }
}
