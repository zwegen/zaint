package de.zwegen.zpaint.test.junit.model

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.model.LayerModelSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerModelSnapshotTest {
    @Test
    fun captureIsIndependentFromLaterModelAndBitmapMutations() {
        val model = createModel()
        val snapshot = LayerModelSnapshot.capture(model)

        model.width = 99
        model.height = 77
        model.getLayerAt(0)!!.bitmap.setPixel(0, 0, Color.MAGENTA)
        model.getLayerAt(0)!!.isVisible = true
        model.getLayerAt(0)!!.opacityPercentage = 100
        model.removeLayerAt(1)
        model.currentLayer = model.getLayerAt(0)

        assertEquals(3, snapshot.width)
        assertEquals(2, snapshot.height)
        assertEquals(2, snapshot.layerCount)
        assertFalse(snapshot.layers[0].isVisible)
        assertEquals(25, snapshot.layers[0].opacityPercentage)
        val copiedBitmap = snapshot.copyBitmapAt(0)
        try {
            assertEquals(Color.RED, copiedBitmap.getPixel(0, 0))
            assertEquals(Color.GREEN, copiedBitmap.getPixel(1, 0))
        } finally {
            copiedBitmap.recycle()
        }

        val restoredModel = ZaintLayerModel()
        try {
            snapshot.restoreInto(restoredModel)
            assertSnapshotState(restoredModel)
        } finally {
            snapshot.release()
        }
    }

    @Test
    fun restorePreservesDimensionsOrderVisibilityOpacityCurrentLayerAndPixels() {
        val sourceModel = createModel()
        val snapshot = LayerModelSnapshot.capture(sourceModel)
        val restoredModel = ZaintLayerModel()

        try {
            snapshot.restoreInto(restoredModel)

            assertSnapshotState(restoredModel)
            assertNotSame(sourceModel.getLayerAt(0), restoredModel.getLayerAt(0))
            assertNotSame(sourceModel.getLayerAt(0)!!.bitmap, restoredModel.getLayerAt(0)!!.bitmap)
        } finally {
            snapshot.release()
        }
    }

    @Test
    fun releaseIsIdempotentAndMakesSnapshotUnavailable() {
        val snapshot = LayerModelSnapshot.capture(createModel())

        assertTrue(snapshot.release())
        assertFalse(snapshot.release())
        assertTrue(snapshot.isReleased)
    }

    private fun createModel(): ZaintLayerModel {
        val firstBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.RED)
            setPixel(1, 0, Color.GREEN)
        }
        val secondBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.BLUE)
            setPixel(2, 1, Color.YELLOW)
        }
        val firstLayer = ZaintLayer(firstBitmap).apply {
            isVisible = false
            opacityPercentage = 25
        }
        val secondLayer = ZaintLayer(secondBitmap).apply {
            isVisible = true
            opacityPercentage = 70
        }

        return ZaintLayerModel().apply {
            width = 3
            height = 2
            addLayerAt(0, firstLayer)
            addLayerAt(1, secondLayer)
            currentLayer = secondLayer
        }
    }

    private fun assertSnapshotState(model: ZaintLayerModel) {
        assertEquals(3, model.width)
        assertEquals(2, model.height)
        assertEquals(2, model.layerCount)
        assertEquals(1, model.getLayerIndexOf(model.currentLayer!!))

        val firstLayer = model.getLayerAt(0)!!
        assertFalse(firstLayer.isVisible)
        assertEquals(25, firstLayer.opacityPercentage)
        assertEquals(Color.RED, firstLayer.bitmap.getPixel(0, 0))
        assertEquals(Color.GREEN, firstLayer.bitmap.getPixel(1, 0))

        val secondLayer = model.getLayerAt(1)!!
        assertTrue(secondLayer.isVisible)
        assertEquals(70, secondLayer.opacityPercentage)
        assertEquals(Color.BLUE, secondLayer.bitmap.getPixel(0, 0))
        assertEquals(Color.YELLOW, secondLayer.bitmap.getPixel(2, 1))
    }
}
