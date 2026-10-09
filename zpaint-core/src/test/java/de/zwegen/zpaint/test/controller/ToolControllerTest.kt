package de.zwegen.zpaint.test.controller

import android.graphics.Color
import android.graphics.Paint
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.controller.ZaintToolCoordinator
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.ToolFactory
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class ZaintToolCoordinatorTest {
    @Mock
    lateinit var toolReference: ToolReference

    @Mock
    lateinit var toolOptionsViewController: ZaintToolOptionsController

    @Mock
    lateinit var toolFactory: ToolFactory

    @Mock
    lateinit var commandManager: ZaintCommandTimeline

    @Mock
    lateinit var workspace: Workspace

    @Mock
    lateinit var toolPaint: ToolPaint

    @Mock
    lateinit var contextCallback: ContextCallback
    lateinit var idlingResource: CountingIdlingResource
    private lateinit var toolController: ZaintToolCoordinator

    @Before
    fun init() {
        idlingResource = CountingIdlingResource("name")
        toolController = ZaintToolCoordinator(
            toolReference,
            toolOptionsViewController,
            toolFactory,
            commandManager,
            workspace,
            idlingResource,
            toolPaint,
            contextCallback
        )
    }

    @Test
    fun toolListIncludesIconTool() {
        Assert.assertTrue(toolController.toolList.contains(ZaintToolKind.ICON))
    }

    @Test
    fun testSetUp() {
        Mockito.verifyNoInteractions(
            toolReference, toolOptionsViewController, toolFactory,
            commandManager, workspace, toolPaint, contextCallback
        )
        Assert.assertNotNull(toolController)
    }

    @Test
    fun testIsBrushDefaultTool() {
        val tool = Mockito.mock(Tool::class.java)
        Mockito.`when`(tool.toolType).thenReturn(ZaintToolKind.BRUSH)
        Mockito.`when`(toolReference.tool).thenReturn(tool)
        Assert.assertTrue(toolController.isDefaultTool)
    }

    @Test
    fun testAllOtherToolsAreNotDefault() {
        val tool = Mockito.mock(Tool::class.java)
        Mockito.`when`(tool.toolType).thenReturn(
            ZaintToolKind.PIPETTE,
            ZaintToolKind.UNDO,
            ZaintToolKind.REDO,
            ZaintToolKind.FILL,
            ZaintToolKind.CLIPBOARD,
            ZaintToolKind.LINE,
            ZaintToolKind.IMPORTPNG,
            ZaintToolKind.TRANSFORM,
            ZaintToolKind.ERASER,
            ZaintToolKind.TEXT,
            ZaintToolKind.LAYER,
            ZaintToolKind.COLORCHOOSER,
            ZaintToolKind.HAND,
            ZaintToolKind.SMUDGE
        )
        Mockito.`when`(toolReference.tool).thenReturn(tool)

        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
        Assert.assertFalse(toolController.isDefaultTool)
    }

    @Test
    fun testHideToolOptionsCallsZaintToolOptionsController() {
        toolController.hideToolOptionsView()
        Mockito.verify(toolOptionsViewController).hide()
        Mockito.verifyNoMoreInteractions(toolOptionsViewController)
    }

    @Test
    fun testHideToolOptionsImmediatelyCallsZaintToolOptionsController() {
        toolController.hideToolOptionsViewImmediately()
        Mockito.verify(toolOptionsViewController).hideImmediately()
        Mockito.verifyNoMoreInteractions(toolOptionsViewController)
    }

    @Test
    fun testZaintToolOptionsControllerWhenOptionsVisibleReturnsTrue() {
        Mockito.`when`(toolOptionsViewController.isVisible).thenReturn(true)
        Assert.assertTrue(toolController.toolOptionsViewVisible())
    }

    @Test
    fun testZaintToolOptionsControllerWhenOptionsNotVisibleReturnsFalse() =
        Assert.assertFalse(toolController.toolOptionsViewVisible())

    @Test
    fun testResetToolInternalStateCallsResetInternalState() {
        val tool = Mockito.mock(Tool::class.java)
        Mockito.`when`(toolReference.tool).thenReturn(tool)
        toolController.resetToolInternalState()
        Mockito.verify(tool).resetInternalState(StateChange.RESET_INTERNAL_STATE)
    }

    @Test
    fun testResetToolInternalStateOnImageLoadedCallsResetInternalState() {
        val tool = Mockito.mock(Tool::class.java)
        Mockito.`when`(toolReference.tool).thenReturn(tool)
        toolController.resetToolInternalStateOnImageLoaded()
        Mockito.verify(tool).resetInternalState(StateChange.NEW_IMAGE_LOADED)
    }

    @Test
    fun testGetToolColorReturnsColor() {
        val tool = Mockito.mock(Tool::class.java)
        val paint = Mockito.mock(Paint::class.java)
        Mockito.`when`(toolReference.tool).thenReturn(tool)
        Mockito.`when`(tool.drawPaint).thenReturn(paint)
        Mockito.`when`(paint.color).thenReturn(Color.CYAN)
        Assert.assertEquals(Color.CYAN, toolController.toolColor)
    }

    @Test
    fun testGetToolTypeReturnsToolType() {
        val tool = Mockito.mock(Tool::class.java)
        Mockito.`when`(toolReference.tool).thenReturn(tool)
        Mockito.`when`(tool.toolType).thenReturn(ZaintToolKind.BRUSH, ZaintToolKind.ERASER)
        Assert.assertEquals(ZaintToolKind.BRUSH, toolController.toolType)
        Assert.assertEquals(ZaintToolKind.ERASER, toolController.toolType)
    }

    @Test
    fun testDisableToolOptionsCallsOptions() {
        toolController.disableToolOptionsView()
        Mockito.verify(toolOptionsViewController).disable()
    }

    @Test
    fun testEnableToolOptionsCallsOptions() {
        toolController.enableToolOptionsView()
        Mockito.verify(toolOptionsViewController).enable()
    }

    @Test
    fun testToggleToolOptionsWhenNotVisibleThenShowOptions() {
        toolController.toggleToolOptionsView()
        Mockito.verify(toolOptionsViewController).show()
    }

    @Test
    fun testToggleToolOptionsWhenVisibleThenHideOptions() {
        Mockito.`when`(toolOptionsViewController.isVisible).thenReturn(true)
        toolController.toggleToolOptionsView()
        Mockito.verify(toolOptionsViewController).hide()
    }

    @Test
    fun testHasToolOptions() {
        val mock = Mockito.mock(Tool::class.java)
        Mockito.`when`(toolReference.tool).thenReturn(mock)
        Mockito.`when`(mock.toolType).thenReturn(ZaintToolKind.BRUSH, ZaintToolKind.IMPORTPNG)
        Assert.assertTrue(toolController.hasToolOptionsView())
        Assert.assertFalse(toolController.hasToolOptionsView())
    }
}
