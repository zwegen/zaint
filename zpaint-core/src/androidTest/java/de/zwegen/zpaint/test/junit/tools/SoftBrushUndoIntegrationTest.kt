package de.zwegen.zpaint.test.junit.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import de.zwegen.zpaint.ZPaintApplication
import de.zwegen.zpaint.command.implementation.ZaintClipboardStamp
import de.zwegen.zpaint.command.implementation.ZaintCommandFactory
import de.zwegen.zpaint.command.implementation.ZaintHistoryManager
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.implementation.ZaintBrushTool
import de.zwegen.zpaint.tools.implementation.ZaintDrawingPaint
import de.zwegen.zpaint.tools.implementation.ZaintWorkspace
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.tools.options.ZaintBrushOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.Perspective
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class SoftBrushUndoIntegrationTest {
    private val allocatedBitmaps = mutableListOf<Bitmap>()
    private var command: ZaintClipboardStamp? = null

    @After
    fun tearDown() {
        command?.freeResources()
        allocatedBitmaps.distinct().forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun smallSoftBrushStrokeStoresOnlyItsAreaAndUndoRedoRestoreEveryPixel() {
        val width = 640
        val height = 480
        ZPaintApplication.cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val before = patternedBitmap(width, height)
        val lowerLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.argb(120, 30, 90, 150))
        }
        allocatedBitmaps += before
        allocatedBitmaps += lowerLayer

        val model = ZaintLayerModel().apply {
            this.width = width
            this.height = height
            addLayerAt(0, ZaintLayer(before.copy(Bitmap.Config.ARGB_8888, true)))
            addLayerAt(1, ZaintLayer(lowerLayer.copy(Bitmap.Config.ARGB_8888, true)))
            currentLayer = getLayerAt(0)
        }
        val commandManager = ZaintHistoryManager(CommonFactory(), model)
        commandManager.setInitialStateCommand(
            ZaintCommandFactory().createInitCommand(
                listOf(
                    ZaintLayer(before.copy(Bitmap.Config.ARGB_8888, true)),
                    ZaintLayer(lowerLayer.copy(Bitmap.Config.ARGB_8888, true))
                )
            )
        )
        val workspace = ZaintWorkspace(model, Perspective(width, height)) { }
        val toolPaint = ZaintDrawingPaint(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            color = Color.argb(180, 220, 40, 90)
        }
        val tool = ZaintBrushTool(
            mock(ZaintBrushOptions::class.java),
            mock(ContextCallback::class.java),
            mock(ZaintToolOptionsController::class.java),
            toolPaint,
            workspace,
            CountingIdlingResource("soft-brush-undo"),
            commandManager,
            0L,
            BrushPreset.BRUSH
        )

        tool.handleDown(PointF(200f, 150f))
        tool.handleMove(PointF(210f, 151f), false)
        tool.handleUp(PointF(220f, 153f))

        command = commandManager.lastExecutedCommand as ZaintClipboardStamp
        assertTrue(
            "A small soft stroke must not retain a full-canvas undo bitmap",
            command!!.boxWidth * command!!.boxHeight < width.toFloat() * height.toFloat()
        )
        val afterStroke = model.currentLayer!!.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        allocatedBitmaps += afterStroke

        commandManager.undo()
        assertBitmapEquals(before, model.currentLayer!!.bitmap)

        commandManager.redo()
        assertBitmapEquals(afterStroke, model.currentLayer!!.bitmap)
    }

    private fun assertBitmapEquals(expected: Bitmap, actual: Bitmap) {
        assertTrue("Bitmap dimensions differ", expected.width == actual.width && expected.height == actual.height)
        val expectedPixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(actual.width * actual.height)
        expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
        assertTrue("Bitmap pixels differ", expectedPixels.contentEquals(actualPixels))
    }

    private fun patternedBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val pixels = IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                Color.argb((x * 17 + y * 13) and 0xff, x and 0xff, y and 0xff, (x xor y) and 0xff)
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
}
