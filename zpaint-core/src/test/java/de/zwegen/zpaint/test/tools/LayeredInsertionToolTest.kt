package de.zwegen.zpaint.test.tools

import android.content.Context
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import android.graphics.Bitmap
import android.graphics.Paint
import android.util.DisplayMetrics
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintClipboardStamp
import de.zwegen.zpaint.command.implementation.ZaintTextStamp
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.IconTool
import de.zwegen.zpaint.tools.implementation.ZaintImportTool
import de.zwegen.zpaint.tools.implementation.ZaintTextTool
import de.zwegen.zpaint.tools.options.IconToolOptionsView
import de.zwegen.zpaint.tools.options.ImportToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintTextOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.Perspective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class LayeredInsertionToolTest {
    @Mock
    private lateinit var contextCallback: ContextCallback

    @Mock
    private lateinit var workspace: Workspace

    @Mock
    private lateinit var layerModel: ZaintLayerContracts.Model

    @Mock
    private lateinit var toolPaint: ToolPaint

    @Mock
    private lateinit var toolOptionsViewController: ZaintToolOptionsController

    @Mock
    private lateinit var commandManager: ZaintCommandTimeline

    @Mock
    private lateinit var textToolOptionsView: ZaintTextOptions

    @Mock
    private lateinit var iconToolOptionsView: IconToolOptionsView

    @Mock
    private lateinit var importToolOptionsView: ImportToolOptionsView

    @Before
    fun setUp() {
        val displayMetrics = DisplayMetrics().apply {
            widthPixels = 1000
            density = 1f
            densityDpi = DisplayMetrics.DENSITY_MEDIUM
        }
        val context = mock(Context::class.java)

        `when`(contextCallback.context).thenReturn(context)
        `when`(contextCallback.displayMetrics).thenReturn(displayMetrics)
        `when`(contextCallback.orientation).thenReturn(ContextCallback.ScreenOrientation.PORTRAIT)
        `when`(contextCallback.scrollTolerance).thenReturn(0)
        `when`(contextCallback.getColor(org.mockito.ArgumentMatchers.anyInt())).thenReturn(0)
        `when`(context.getString(org.mockito.ArgumentMatchers.anyInt())).thenReturn("Text")
        `when`(workspace.width).thenReturn(500)
        `when`(workspace.height).thenReturn(500)
        `when`(workspace.scale).thenReturn(1f)
        `when`(workspace.perspective).thenReturn(Perspective(500, 500))
        `when`(workspace.layerModel).thenReturn(layerModel)
        `when`(layerModel.layerCount).thenReturn(2)
        whenever(workspace.intersectsWith(any())).thenReturn(true)
        `when`(toolPaint.paint).thenReturn(Paint())
        `when`(toolPaint.previewColor).thenReturn(0)
    }

    @Test
    fun textInsertionAddsTextCommandToCurrentLayer() {
        val textTool = ZaintTextTool(
            textToolOptionsView,
            contextCallback,
            toolOptionsViewController,
            toolPaint,
            workspace,
            CountingIdlingResource("text"),
            commandManager,
            0L
        )

        textTool.onClickOnButton()

        assertCurrentLayerCommand(ZaintTextStamp::class.java)
    }

    @Test
    fun iconInsertionAddsClipboardCommandToCurrentLayer() {
        val iconTool = IconTool(
            iconToolOptionsView,
            contextCallback,
            toolOptionsViewController,
            toolPaint,
            workspace,
            CountingIdlingResource("icon"),
            commandManager,
            0L
        )
        iconTool.setBitmap(mock(Bitmap::class.java))

        iconTool.onClickOnButton()

        assertCurrentLayerCommand(ZaintClipboardStamp::class.java)
    }

    @Test
    fun iconToolUsesDedicatedIconToolType() {
        val iconTool = IconTool(
            iconToolOptionsView,
            contextCallback,
            toolOptionsViewController,
            toolPaint,
            workspace,
            CountingIdlingResource("icon"),
            commandManager,
            0L
        )

        assertEquals(ZaintToolKind.ICON, iconTool.toolType)
    }

    private fun assertCurrentLayerCommand(contentCommandType: Class<out Command>) {
        val commandCaptor = ArgumentCaptor.forClass(Command::class.java)
        verify(commandManager).addCommand(commandCaptor.capture())

        assertTrue(contentCommandType.isInstance(commandCaptor.value))
    }
}
