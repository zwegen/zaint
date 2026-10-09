package de.zwegen.zpaint.test.command.implementation

import org.mockito.Mockito
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import org.hamcrest.CoreMatchers
import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.model.ZaintLayer
import org.junit.Assert
import org.junit.Test

class CompositeCommandTest {
    @Test
    fun testRunEmpty() {
        val canvas = Mockito.mock(Canvas::class.java)
        val layerModel = ZaintLayerModel()
        val command = ZaintCommandBatch()

        command.run(canvas, layerModel)

        Mockito.verifyNoInteractions(canvas)
        Assert.assertThat(layerModel.layerCount, CoreMatchers.`is`(0))
        Assert.assertNull(layerModel.currentLayer)
    }

    @Test
    fun testRunAfterAddWithoutCurrentLayer() {
        val canvas = Mockito.mock(Canvas::class.java)
        val layerModel = ZaintLayerModel()
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = Mockito.mock(Command::class.java)
        val command = ZaintCommandBatch()

        command.addCommand(firstCommand)
        command.addCommand(secondCommand)
        command.run(canvas, layerModel)

        Mockito.verifyNoInteractions(canvas)

        val inOrder = Mockito.inOrder(firstCommand, secondCommand)
        inOrder.verify(firstCommand).run(canvas, layerModel)
        inOrder.verify(secondCommand).run(canvas, layerModel)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun testRunAfterAddWithCurrentLayerSet() {
        val canvas = Mockito.mock(Canvas::class.java)
        val layerModel = ZaintLayerModel()
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayer::class.java)
        val currentBitmap = Mockito.mock(Bitmap::class.java)

        Mockito.`when`(currentLayer.bitmap).thenReturn(currentBitmap)
        layerModel.currentLayer = currentLayer

        val command = ZaintCommandBatch()
        command.addCommand(firstCommand)
        command.addCommand(secondCommand)
        command.run(canvas, layerModel)

        val inOrder = Mockito.inOrder(firstCommand, secondCommand, canvas)
        inOrder.verify(canvas).setBitmap(currentBitmap)
        inOrder.verify(firstCommand).run(canvas, layerModel)
        inOrder.verify(canvas).setBitmap(currentBitmap)
        inOrder.verify(secondCommand).run(canvas, layerModel)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun testRunPreparesCanvasForLayerSelectedByPreviousCommand() {
        val canvas = Mockito.mock(Canvas::class.java)
        val layerModel = ZaintLayerModel()
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val firstBitmap = Mockito.mock(Bitmap::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        val secondBitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(firstLayer.bitmap).thenReturn(firstBitmap)
        Mockito.`when`(secondLayer.bitmap).thenReturn(secondBitmap)
        layerModel.currentLayer = firstLayer
        val selectingCommand = object : Command {
            override fun run(canvas: Canvas, layerModel: de.zwegen.zpaint.contract.ZaintLayerContracts.Model) {
                layerModel.currentLayer = secondLayer
            }

            override fun freeResources() = Unit
        }
        val nextCommand = Mockito.mock(Command::class.java)
        val command = ZaintCommandBatch().apply {
            addCommand(selectingCommand)
            addCommand(nextCommand)
        }

        command.run(canvas, layerModel)

        val inOrder = Mockito.inOrder(canvas, nextCommand)
        inOrder.verify(canvas).setBitmap(firstBitmap)
        inOrder.verify(canvas).setBitmap(secondBitmap)
        inOrder.verify(nextCommand).run(canvas, layerModel)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun testFreeResourcesEmpty() {
        val command = ZaintCommandBatch()
        command.freeResources()
    }

    @Test
    fun testFreeResourcesAfterAdd() {
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = Mockito.mock(Command::class.java)
        val command = ZaintCommandBatch()

        command.addCommand(firstCommand)
        command.addCommand(secondCommand)
        command.freeResources()

        val inOrder = Mockito.inOrder(firstCommand, secondCommand)
        inOrder.verify(firstCommand).freeResources()
        inOrder.verify(secondCommand).freeResources()
        inOrder.verifyNoMoreInteractions()
    }
}
