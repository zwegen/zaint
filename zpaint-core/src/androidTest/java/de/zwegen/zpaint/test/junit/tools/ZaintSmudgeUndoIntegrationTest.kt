package de.zwegen.zpaint.test.junit.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import de.zwegen.zpaint.ZPaintApplication
import de.zwegen.zpaint.command.implementation.ZaintCommandFactory
import de.zwegen.zpaint.command.implementation.ZaintHistoryManager
import de.zwegen.zpaint.command.implementation.ZaintSmudgeStroke
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.implementation.ZaintDrawingPaint
import de.zwegen.zpaint.tools.implementation.ZaintWorkspace
import de.zwegen.zpaint.tools.implementation.ZaintSmudgeTool
import de.zwegen.zpaint.tools.options.ZaintSmudgeOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.Perspective
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ZaintSmudgeUndoIntegrationTest {
    private val allocatedBitmaps = mutableListOf<Bitmap>()
    private var command: ZaintSmudgeStroke? = null

    @After
    fun tearDown() {
        command?.freeResources()
        allocatedBitmaps.distinct().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    @Test
    fun middleStrokeStoresPaddedAreaSmallerThanLayerAndUndoRedoRestoreEveryPixel() {
        val result = smudge(listOf(PointF(100f, 100f), PointF(120f, 108f)), 640, 480)
        assertTrue(result.command.affectedBounds.width * result.command.affectedBounds.height < 640 * 480)
        assertEquals(84, result.command.affectedBounds.left)
        assertEquals(84, result.command.affectedBounds.top)
        assertPixels(result.before, result.afterUndo)
        assertPixels(result.after, result.afterRedo)
    }

    @Test
    fun edgeStrokeStoresClippedPaddedAreaAndUndoRedoRestoreEveryPixel() {
        val result = smudge(listOf(PointF(2f, 2f), PointF(10f, 10f)), 640, 480)
        assertEquals(0, result.command.affectedBounds.left)
        assertEquals(0, result.command.affectedBounds.top)
        assertTrue(result.command.affectedBounds.right <= 640)
        assertTrue(result.command.affectedBounds.bottom <= 480)
        assertPixels(result.before, result.afterUndo)
        assertPixels(result.after, result.afterRedo)
    }

    @Test
    fun shortPointStoresOnlyItsPaddedAreaAndUndoRedoRestoreEveryPixel() {
        val result = smudge(listOf(PointF(100f, 100f)), 640, 480)
        assertEquals(32 * 32, result.command.originalBitmap.width * result.command.originalBitmap.height)
        assertPixels(result.before, result.afterUndo)
        assertPixels(result.after, result.afterRedo)
    }

    @Test
    fun longStrokeOnTransparentCurrentLayerKeepsOtherLayerAndUndoRedoRestoreEveryPixel() {
        val result = smudge(listOf(PointF(100f, 100f), PointF(300f, 200f)), 640, 480)
        assertEquals(84, result.command.affectedBounds.left)
        assertEquals(84, result.command.affectedBounds.top)
        assertEquals(316, result.command.affectedBounds.right)
        assertEquals(216, result.command.affectedBounds.bottom)
        assertPixels(result.before, result.afterUndo)
        assertPixels(result.after, result.afterRedo)
        assertPixels(result.lowerLayerBefore, result.lowerLayerAfterRedo)
    }

    private fun smudge(points: List<PointF>, width: Int, height: Int): Result {
        ZPaintApplication.cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val before = patternedBitmap(width, height)
        val lower = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.argb(120, 30, 90, 150))
        }
        allocatedBitmaps += before
        allocatedBitmaps += lower
        val model = ZaintLayerModel().apply {
            this.width = width
            this.height = height
            addLayerAt(0, ZaintLayer(before.copy(Bitmap.Config.ARGB_8888, true)))
            addLayerAt(1, ZaintLayer(lower.copy(Bitmap.Config.ARGB_8888, true)))
            currentLayer = getLayerAt(0)
        }
        val manager = ZaintHistoryManager(CommonFactory(), model)
        manager.setInitialStateCommand(ZaintCommandFactory().createInitCommand(listOf(
            ZaintLayer(before.copy(Bitmap.Config.ARGB_8888, true)),
            ZaintLayer(lower.copy(Bitmap.Config.ARGB_8888, true))
        )))
        val tool = ZaintSmudgeTool(
            mock(ZaintSmudgeOptions::class.java),
            mock(ContextCallback::class.java),
            mock(ZaintToolOptionsController::class.java),
            ZaintDrawingPaint(InstrumentationRegistry.getInstrumentation().targetContext).apply { strokeWidth = 30f },
            ZaintWorkspace(model, Perspective(width, height)) { },
            CountingIdlingResource("smudge-undo"),
            manager
        )
        assertTrue(tool.handleDown(points.first()))
        points.drop(1).forEach { assertTrue(tool.handleMove(it, false)) }
        assertTrue(tool.handleUp(points.last()))
        command = manager.lastExecutedCommand as ZaintSmudgeStroke
        val after = model.currentLayer!!.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val lowerLayerBefore = model.getLayerAt(1)!!.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        allocatedBitmaps += after
        allocatedBitmaps += lowerLayerBefore
        manager.undo()
        val afterUndo = model.currentLayer!!.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        manager.redo()
        val afterRedo = model.currentLayer!!.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val lowerLayerAfterRedo = model.getLayerAt(1)!!.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        allocatedBitmaps += afterUndo
        allocatedBitmaps += afterRedo
        allocatedBitmaps += lowerLayerAfterRedo
        return Result(command!!, before, after, afterUndo, afterRedo, lowerLayerBefore, lowerLayerAfterRedo)
    }

    private fun assertPixels(expected: Bitmap, actual: Bitmap) {
        val expectedPixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(actual.width * actual.height)
        expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
        assertTrue(expectedPixels.contentEquals(actualPixels))
    }

    private fun patternedBitmap(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.setPixels(IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            Color.argb((x * 17 + y * 13) and 0xff, x and 0xff, y and 0xff, (x xor y) and 0xff)
        }, 0, width, 0, 0, width, height)
    }

    private data class Result(
        val command: ZaintSmudgeStroke,
        val before: Bitmap,
        val after: Bitmap,
        val afterUndo: Bitmap,
        val afterRedo: Bitmap,
        val lowerLayerBefore: Bitmap,
        val lowerLayerAfterRedo: Bitmap
    )
}
