package de.zwegen.zpaint.test.junit.command

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import de.zwegen.zpaint.command.implementation.ZaintAddLayerCommand
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.implementation.ZaintColorChange
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.implementation.ZaintDocumentCrop
import de.zwegen.zpaint.command.implementation.ZaintCommandFactory
import de.zwegen.zpaint.command.implementation.ZaintLayerMirror
import de.zwegen.zpaint.command.implementation.ZaintMirrorDirection
import de.zwegen.zpaint.command.implementation.ZaintLayerMerge
import de.zwegen.zpaint.command.implementation.ZaintPointCommand
import de.zwegen.zpaint.command.implementation.ZaintRemoveLayerCommand
import de.zwegen.zpaint.command.implementation.ZaintLayerReorder
import de.zwegen.zpaint.command.implementation.ZaintDocumentResize
import de.zwegen.zpaint.command.implementation.ZaintDocumentRotation
import de.zwegen.zpaint.command.implementation.ZaintRotationDirection
import de.zwegen.zpaint.command.implementation.ZaintSelectLayerCommand
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.implementation.ZaintActiveToolReference
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class DefaultCommandFactoryTest {
    private var commandFactory: ZaintCommandFactory? = null

    @Mock
    private val colorChangeTarget: ColorChangeTarget? = null
    @Before
    fun setUp() {
        commandFactory = ZaintCommandFactory()
    }

    @Test
    fun testCreateInitCommand() {
        val command = commandFactory!!.createInitCommand(10, 20)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintCommandBatch::class.java)))
    }

    @Test
    fun testCreateInitCommandWithBitmap() {
        val command = commandFactory!!.createInitCommand(Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888))
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintCommandBatch::class.java)))
    }

    @Test
    fun testCreateResetCommand() {
        val command = commandFactory!!.createResetCommand()
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintCommandBatch::class.java)))
    }

    @Test
    fun testCreateAddLayerCommand() {
        val command = commandFactory!!.createAddEmptyLayerCommand()
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintAddLayerCommand::class.java)))
    }

    @Test
    fun testCreateSelectLayerCommand() {
        val command = commandFactory!!.createSelectLayerCommand(0)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintSelectLayerCommand::class.java)))
    }

    @Test
    fun testCreateRemoveLayerCommand() {
        val command = commandFactory!!.createRemoveLayerCommand(0)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintRemoveLayerCommand::class.java)))
    }

    @Test
    fun testCreateReorderLayersCommand() {
        val command = commandFactory!!.createReorderLayersCommand(0, 1)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintLayerReorder::class.java)))
    }

    @Test
    fun testCreateMergeLayersCommand() {
        val command = commandFactory!!.createMergeLayersCommand(0, 1)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintLayerMerge::class.java)))
    }

    @Test
    fun testCreateRotateCommand() {
        val command = commandFactory!!.createRotateCommand(ZaintRotationDirection.LEFT)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintDocumentRotation::class.java)))
    }

    @Test
    fun testCreateFlipCommand() {
        val command = commandFactory!!.createFlipCommand(ZaintMirrorDirection.HORIZONTAL)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintLayerMirror::class.java)))
    }

    @Test
    fun testCreateCropCommand() {
        val command = commandFactory!!.createCropCommand(0, 0, 1, 1, 2)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintDocumentCrop::class.java)))
    }

    @Test
    fun testCreatePointCommand() {
        val command = commandFactory!!.createPointCommand(Paint(), PointF(3f, 5f))
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintPointCommand::class.java)))
    }

    @Test
    fun testCreateResizeCommand() {
        val command = commandFactory!!.createResizeCommand(10, 20)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintDocumentResize::class.java)))
    }

    @Test
    fun testCreateColorChangedCommand() {
        val toolReference: ToolReference = ZaintActiveToolReference()
        MockitoAnnotations.initMocks(this)
        val command = commandFactory!!.createColorChangedCommand(toolReference.tool, colorChangeTarget!!, Color.WHITE)
        Assert.assertThat(command, CoreMatchers.`is`(CoreMatchers.instanceOf(ZaintColorChange::class.java)))
    }
}
