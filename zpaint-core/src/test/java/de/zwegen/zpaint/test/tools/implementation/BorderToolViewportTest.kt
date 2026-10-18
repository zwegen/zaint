package de.zwegen.zpaint.test.tools.implementation

import android.graphics.Paint
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.BorderTool
import de.zwegen.zpaint.tools.options.BorderToolOptionsView
import de.zwegen.zpaint.ui.Perspective
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BorderToolViewportTest {
    private lateinit var perspective: Perspective
    private lateinit var callback: BorderToolOptionsView.Callback
    private lateinit var tool: BorderTool

    @Before
    fun setUp() {
        val workspace = mock<Workspace>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(workspace.layerModel.width).thenReturn(1000)
        whenever(workspace.layerModel.height).thenReturn(750)
        perspective = workspace.perspective
        val toolPaint = mock<ToolPaint>()
        whenever(toolPaint.paint).thenReturn(Paint())
        val options = mock<BorderToolOptionsView>()
        tool = BorderTool(
            options, mock(), mock(), toolPaint, workspace,
            CountingIdlingResource("BorderToolViewportTest"), mock()
        )
        val callbackCaptor = argumentCaptor<BorderToolOptionsView.Callback>()
        verify(options).setCallback(callbackCaptor.capture())
        callback = callbackCaptor.firstValue
    }

    @Test
    fun openingToolDoesNotChangePerspective() {
        verifyNoInteractions(perspective)
    }

    @Test
    fun growingBorderDoesNotChangePerspective() {
        clearInvocations(perspective)
        for (amount in intArrayOf(20, 80, 0)) {
            callback.onBorderAmountChanged(amount)
        }
        verifyNoInteractions(perspective)
    }

    @Test
    fun croppingBorderDoesNotChangePerspective() {
        clearInvocations(perspective)
        callback.onBorderAmountChanged(-20)
        callback.onBorderAmountChanged(-40)
        verifyNoInteractions(perspective)
    }

    @Test
    fun sliderTrackingDoesNotChangePerspective() {
        clearInvocations(perspective)
        callback.onStartTracking()
        callback.onBorderAmountChanged(40)
        callback.onStopTracking(40)
        verifyNoInteractions(perspective)
    }

    @Test
    fun leavingToolDoesNotChangePerspective() {
        callback.onBorderAmountChanged(40)
        clearInvocations(perspective)
        tool.resetInternalState(StateChange.RESET_INTERNAL_STATE)
        verifyNoInteractions(perspective)
    }
}
