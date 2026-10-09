package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintSelectionClear
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.test.utils.ZPaintAsserts
import org.junit.Before
import org.junit.Test

class CutCommandTest {
    private lateinit var commandUnderTest: Command
    private lateinit var paintUnderTest: Paint
    private lateinit var canvasUnderTest: Canvas
    private lateinit var bitmapUnderTest: Bitmap
    private lateinit var canvasBitmapUnderTest: Bitmap

    companion object {
        private const val BITMAP_BASE_COLOR = Color.GREEN
        private const val INITIAL_HEIGHT = 80
        private const val INITIAL_WIDTH = 80
        private val POINT = Point(0, 0)
    }

    @Before
    fun setUp() {
        val layerModel = ZaintLayerModel()
        layerModel.width = INITIAL_WIDTH
        layerModel.height = INITIAL_HEIGHT
        canvasBitmapUnderTest = Bitmap.createBitmap(INITIAL_WIDTH, INITIAL_HEIGHT, Bitmap.Config.ARGB_8888)
        canvasBitmapUnderTest.eraseColor(BITMAP_BASE_COLOR)
        bitmapUnderTest = canvasBitmapUnderTest.copy(Bitmap.Config.ARGB_8888, true)
        val layerUnderTest = ZaintLayer(bitmapUnderTest)

        paintUnderTest = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            alpha = 0
        }
        canvasUnderTest = Canvas()
        canvasUnderTest.setBitmap(canvasBitmapUnderTest)
        layerModel.addLayerAt(0, layerUnderTest)
        layerModel.currentLayer = layerUnderTest
        commandUnderTest = ZaintSelectionClear(POINT, 30f, 30f, 0f)
    }

    @Test
    fun testRun() {
        Canvas(bitmapUnderTest).drawRect(RectF(0f, 0f, 30f / 2, 30f / 2), paintUnderTest)
        commandUnderTest.run(canvasUnderTest, ZaintLayerModel())
        ZPaintAsserts.assertBitmapEquals(bitmapUnderTest, canvasBitmapUnderTest)
    }
}
