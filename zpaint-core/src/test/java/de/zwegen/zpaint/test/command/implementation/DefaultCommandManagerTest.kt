package de.zwegen.zpaint.test.command.implementation

import org.junit.runner.RunWith
import org.mockito.Mock
import de.zwegen.zpaint.command.ZaintCommandTimeline.CommandListener
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.command.implementation.ZaintHistoryManager
import org.junit.Before
import org.mockito.Mockito
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.implementation.ZaintColorChange
import de.zwegen.zpaint.tools.Tool

@RunWith(MockitoJUnitRunner::class)
class DefaultCommandManagerTest {
    @Mock
    private val commandListener: CommandListener? = null

    @Mock
    private lateinit var commonFactory: CommonFactory
    private lateinit var layerModel: ZaintLayerModel
    private lateinit var commandManager: ZaintHistoryManager

    @Before
    fun setUp() {
        layerModel = ZaintLayerModel()
        commandManager = ZaintHistoryManager(commonFactory, layerModel)
    }

    @Test
    fun testAddCommandListener() {
        commandManager.addCommandListener(commandListener!!)
        Mockito.verifyNoInteractions(commandListener)
    }

    @Test
    fun testRemoveCommandListener() {
        commandManager.removeCommandListener(commandListener!!)
        Mockito.verifyNoInteractions(commandListener)
    }

    @Test
    fun testUndoInitiallyNotAvailable() { Assert.assertFalse(commandManager.isUndoAvailable) }

    @Test
    fun testRedoInitiallyNotAvailable() { Assert.assertFalse(commandManager.isRedoAvailable) }

    @Test
    fun testAddCommand() {
        val command = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        layerModel.currentLayer = currentLayer
        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.addCommandListener(commandListener!!)
        commandManager.addCommand(command)

        Mockito.verify(command).run(any<Canvas>(), eq(layerModel))
        Mockito.verify(commandListener).commandPostExecute()

        Assert.assertFalse(commandManager.isRedoAvailable)
        Assert.assertTrue(commandManager.isUndoAvailable)
    }

    @Test
    fun testUndoWhenOnlyOneCommand() {
        val command = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)
        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.addCommandListener(commandListener!!)
        commandManager.addCommand(command)
        commandManager.undo()

        val inOrder = Mockito.inOrder(command, commandListener)
        inOrder.verify(command).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(commandListener, Mockito.times(2)).commandPostExecute()

        Assert.assertTrue(commandManager.isRedoAvailable)
        Assert.assertFalse(commandManager.isUndoAvailable)
    }

    @Test
    fun testUndoWithMultipleCommands() {
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = Mockito.mock(Command::class.java)
        val thirdCommand = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)
        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.addCommand(firstCommand)
        commandManager.addCommand(secondCommand)
        commandManager.addCommand(thirdCommand)
        commandManager.undo()

        val inOrder = Mockito.inOrder(firstCommand, secondCommand, thirdCommand)
        inOrder.verify(firstCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(secondCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(thirdCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(firstCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(secondCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verifyNoMoreInteractions()

        Assert.assertTrue(commandManager.isRedoAvailable)
        Assert.assertTrue(commandManager.isUndoAvailable)
    }

    @Test
    fun testRedo() {
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = Mockito.mock(Command::class.java)
        val thirdCommand = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)

        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.addCommand(firstCommand)
        commandManager.addCommand(secondCommand)
        commandManager.addCommand(thirdCommand)
        commandManager.undo()
        commandManager.redo()

        val inOrder = Mockito.inOrder(firstCommand, secondCommand, thirdCommand)
        inOrder.verify(firstCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(secondCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(thirdCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(firstCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(secondCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(thirdCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verifyNoMoreInteractions()

        Assert.assertFalse(commandManager.isRedoAvailable)
        Assert.assertTrue(commandManager.isUndoAvailable)
    }

    @Test
    fun testRedoMultipleCommands() {
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = Mockito.mock(Command::class.java)
        val thirdCommand = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)
        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))

        commandManager.addCommand(firstCommand)
        commandManager.addCommand(secondCommand)
        commandManager.addCommand(thirdCommand)
        commandManager.undo()
        layerModel.addLayerAt(0, currentLayer)
        commandManager.undo()
        layerModel.addLayerAt(0, currentLayer)
        commandManager.undo()
        commandManager.redo()
        commandManager.redo()

        val inOrder = Mockito.inOrder(firstCommand, secondCommand, thirdCommand)
        inOrder.verify(firstCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(secondCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(thirdCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(firstCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(secondCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(firstCommand, Mockito.times(2)).run(any<Canvas>(), eq(layerModel))
        inOrder.verify(secondCommand).run(any<Canvas>(), eq(layerModel))
        inOrder.verifyNoMoreInteractions()

        Assert.assertTrue(commandManager.isRedoAvailable)
        Assert.assertTrue(commandManager.isUndoAvailable)
        commandManager.redo()
        Assert.assertFalse(commandManager.isRedoAvailable)
        Assert.assertTrue(commandManager.isUndoAvailable)
    }

    @Test
    fun testRedoClearedOnCommand() {
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = Mockito.mock(Command::class.java)
        val thirdCommand = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)
        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))

        commandManager.addCommand(firstCommand)
        commandManager.addCommand(secondCommand)
        commandManager.undo()

        Assert.assertTrue(commandManager.isRedoAvailable)
        Assert.assertTrue(commandManager.isUndoAvailable)
        commandManager.addCommand(thirdCommand)
        Assert.assertFalse(commandManager.isRedoAvailable)
        Assert.assertTrue(commandManager.isUndoAvailable)
    }

    @Test
    fun testInitialStateCommandOnReset() {
        val initialStateCommand = Mockito.mock(Command::class.java)
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.addLayerAt(0, layer)
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.addCommandListener(commandListener!!)
        commandManager.setInitialStateCommand(initialStateCommand)
        commandManager.reset()

        Mockito.verify(commandListener).commandPostExecute()
        Mockito.verify(initialStateCommand).run(any<Canvas>(), eq(layerModel))
        Assert.assertThat(layerModel.layerCount, CoreMatchers.`is`(0))
    }

    @Test
    fun testInitialStateCommandOnUndo() {
        val initialStateCommand = Mockito.mock(Command::class.java)
        val command = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)

        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.setInitialStateCommand(initialStateCommand)
        commandManager.addCommand(command)
        commandManager.undo()

        Mockito.verify(initialStateCommand).run(any<Canvas>(), eq(layerModel))
    }

    @Test
    fun testUndoClearedOnReset() {
        val command = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.addCommand(command)
        commandManager.reset()

        Assert.assertFalse(commandManager.isRedoAvailable)
        Assert.assertFalse(commandManager.isUndoAvailable)
    }

    @Test
    fun testRedoClearedOnReset() {
        val command = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))

        commandManager.addCommand(command)
        commandManager.undo()
        commandManager.reset()
        Assert.assertFalse(commandManager.isRedoAvailable)
        Assert.assertFalse(commandManager.isUndoAvailable)
    }

    @Test
    fun testIsLastColorCommandUndone() {
        val firstCommand = Mockito.mock(Command::class.java)
        val secondCommand = ZaintColorChange(
            Mockito.mock(Tool::class.java),
            Mockito.mock(ColorChangeTarget::class.java),
            Color.BLUE
        )
        val thirdCommand = Mockito.mock(Command::class.java)
        val currentLayer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)

        layerModel.currentLayer = currentLayer
        layerModel.addLayerAt(0, currentLayer)
        Mockito.`when`(currentLayer.bitmap).thenReturn(Mockito.mock(Bitmap::class.java))
        Mockito.`when`(commonFactory.createCanvas()).thenReturn(Mockito.mock(Canvas::class.java))
        commandManager.addCommand(firstCommand)
        commandManager.addCommand(secondCommand)
        commandManager.addCommand(thirdCommand)

        Assert.assertFalse(commandManager.isLastColorCommandOnTop())
        commandManager.undo()
        Assert.assertTrue(commandManager.isLastColorCommandOnTop())
    }
}
