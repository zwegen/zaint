package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas
import android.graphics.Point
import de.zwegen.zpaint.command.cut.CanvasCutRenderer
import de.zwegen.zpaint.command.cut.CutSelection
import de.zwegen.zpaint.command.cut.CutShape
import de.zwegen.zpaint.command.implementation.ZaintSelectionClear
import de.zwegen.zpaint.command.implementation.ZaintSelectionShape
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.mockito.Mockito.mock

class ZaintSelectionClearTest {
    @Test
    fun `passes rotated oval selection to the canvas adapter`() {
        val renderer = RecordingRenderer()
        val center = Point(12, 20)
        val command = ZaintSelectionClear(
            center,
            boxWidth = 30f,
            boxHeight = 14f,
            boxRotation = 45f,
            shape = ZaintSelectionShape.OVAL,
            cutRenderer = renderer
        )

        command.run(mock(Canvas::class.java), ZaintLayerModel())

        val selection = requireNotNull(renderer.selection)
        assertNotSame(center, selection.center)
        assertEquals(30f, selection.width)
        assertEquals(14f, selection.height)
        assertEquals(45f, selection.rotation)
        assertEquals(CutShape.OVAL, selection.shape)
    }

    @Test
    fun `keeps a free path selection distinct from rectangle and oval`() {
        val renderer = RecordingRenderer()
        val command = ZaintSelectionClear(
            Point(0, 0),
            boxWidth = 1f,
            boxHeight = 1f,
            boxRotation = 0f,
            shape = ZaintSelectionShape.PATH,
            cutRenderer = renderer
        )

        command.run(mock(Canvas::class.java), ZaintLayerModel())

        assertEquals(CutShape.PATH, renderer.selection?.shape)
    }

    private class RecordingRenderer : CanvasCutRenderer {
        var selection: CutSelection? = null

        override fun clear(canvas: Canvas, selection: CutSelection) {
            this.selection = selection
        }
    }
}
