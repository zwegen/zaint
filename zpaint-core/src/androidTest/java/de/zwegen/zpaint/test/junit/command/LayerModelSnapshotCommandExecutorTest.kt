package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.zwegen.zpaint.command.implementation.ZaintAddLayerCommand
import de.zwegen.zpaint.command.implementation.ZaintPointCommand
import de.zwegen.zpaint.command.replay.LayerModelSnapshotCommandExecutor
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.model.LayerModelSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerModelSnapshotCommandExecutorTest {
    @Test
    fun executeCreatesIndependentSnapshotForBitmapAndLayerStructureCommands() {
        val originalModel = ZaintLayerModel().apply {
            width = 2
            height = 2
            addLayerAt(0, ZaintLayer(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, Color.RED)
            }))
            currentLayer = getLayerAt(0)
        }
        val startSnapshot = LayerModelSnapshot.capture(originalModel)
        val executor = LayerModelSnapshotCommandExecutor(CommonFactory())

        val resultSnapshot = executor.execute(
            startSnapshot,
            listOf(
                ZaintAddLayerCommand(CommonFactory()),
                ZaintPointCommand(Paint().apply { color = Color.GREEN }, PointF(0f, 0f))
            )
        )

        try {
            assertEquals(1, originalModel.layerCount)
            assertEquals(Color.RED, originalModel.getLayerAt(0)!!.bitmap.getPixel(0, 0))
            assertEquals(1, startSnapshot.layerCount)
            assertEquals(Color.RED, snapshotPixelAt(startSnapshot, 0, 0, 0))

            assertEquals(2, resultSnapshot.layerCount)
            assertEquals(0, resultSnapshot.currentLayerIndex)
            assertEquals(Color.GREEN, snapshotPixelAt(resultSnapshot, 0, 0, 0))
            assertEquals(Color.RED, snapshotPixelAt(resultSnapshot, 1, 0, 0))
        } finally {
            resultSnapshot.release()
            startSnapshot.release()
            originalModel.layers.forEach { layer ->
                if (!layer.bitmap.isRecycled) {
                    layer.bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun executeRejectsUnsupportedCommandsWithoutReleasingTheStartSnapshot() {
        val startSnapshot = LayerModelSnapshot.capture(ZaintLayerModel().apply {
            width = 1
            height = 1
            addLayerAt(0, ZaintLayer(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
            currentLayer = getLayerAt(0)
        })

        try {
            val thrown = try {
                LayerModelSnapshotCommandExecutor(CommonFactory()).execute(startSnapshot, listOf(UnsupportedCommand()))
                null
            } catch (error: IllegalArgumentException) {
                error
            }

            assertTrue(thrown?.message?.contains("not supported") == true)
            assertFalse(startSnapshot.isReleased)
            assertEquals(1, startSnapshot.layerCount)
        } finally {
            startSnapshot.release()
        }
    }

    private fun snapshotPixelAt(snapshot: LayerModelSnapshot, layerIndex: Int, x: Int, y: Int): Int {
        val bitmap = snapshot.copyBitmapAt(layerIndex)
        return try {
            bitmap.getPixel(x, y)
        } finally {
            bitmap.recycle()
        }
    }

    private class UnsupportedCommand : de.zwegen.zpaint.command.Command {
        override fun run(canvas: android.graphics.Canvas, layerModel: de.zwegen.zpaint.contract.ZaintLayerContracts.Model) = Unit
        override fun freeResources() = Unit
    }
}
