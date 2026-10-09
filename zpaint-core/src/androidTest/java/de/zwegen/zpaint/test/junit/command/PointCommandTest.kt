package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.graphics.PointF
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintPointCommand
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.test.utils.ZPaintAsserts.assertBitmapEquals
import org.junit.Before
import org.junit.Test

class PointCommandTest {
    private lateinit var commandUnderTest: Command
    private var paintUnderTest: Paint? = null
    private var pointUnderTest: PointF? = null
    private var canvasUnderTest: Canvas? = null
    private lateinit var bitmapUnderTest: Bitmap
    private lateinit var canvasBitmapUnderTest: Bitmap
    @Before
    fun setUp() {
        val layerModel = ZaintLayerModel()
        layerModel.width = INITIAL_WIDTH
        layerModel.height = INITIAL_HEIGHT
        canvasBitmapUnderTest = Bitmap.createBitmap(INITIAL_WIDTH, INITIAL_HEIGHT, Bitmap.Config.ARGB_8888)
        canvasBitmapUnderTest.eraseColor(BITMAP_BASE_COLOR)
        bitmapUnderTest = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        val layerUnderTest = ZaintLayer(bitmapUnderTest)
        canvasUnderTest = Canvas()
        canvasUnderTest!!.setBitmap(canvasBitmapUnderTest)
        paintUnderTest = Paint()
        paintUnderTest!!.color = PAINT_BASE_COLOR
        paintUnderTest!!.strokeWidth = 0f
        paintUnderTest!!.style = Paint.Style.STROKE
        paintUnderTest!!.strokeCap = Cap.BUTT
        pointUnderTest = PointF((INITIAL_WIDTH / 2).toFloat(), (INITIAL_HEIGHT / 2).toFloat())
        layerModel.addLayerAt(0, layerUnderTest)
        layerModel.currentLayer = layerUnderTest
        commandUnderTest = ZaintPointCommand(paintUnderTest!!, pointUnderTest!!)
    }

    @Test
    fun testRun() {
        bitmapUnderTest!!.setPixel(pointUnderTest!!.x.toInt(), pointUnderTest!!.y.toInt(), paintUnderTest!!.color)
        commandUnderTest!!.run(canvasUnderTest!!, ZaintLayerModel())
        assertBitmapEquals(bitmapUnderTest!!, canvasBitmapUnderTest)
    }

    @Test
    fun testRunOutOfBounds() {
        pointUnderTest = PointF((canvasBitmapUnderTest!!.height + 1).toFloat(), (canvasBitmapUnderTest!!.width + 1).toFloat())
        commandUnderTest = ZaintPointCommand(paintUnderTest!!, pointUnderTest!!)
        commandUnderTest.run(canvasUnderTest!!, ZaintLayerModel())
        assertBitmapEquals(bitmapUnderTest!!, canvasBitmapUnderTest)
    }

    companion object {
        private const val BITMAP_BASE_COLOR = Color.GREEN
        private const val PAINT_BASE_COLOR = Color.BLUE
        private const val INITIAL_HEIGHT = 80
        private const val INITIAL_WIDTH = 80
    }
}
