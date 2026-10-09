package de.zwegen.zpaint.test.tools

import android.graphics.Paint.Cap
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.implementation.ToolPaintControls
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ToolPaintControlsTest {
    private val toolPaint = mock(ToolPaint::class.java)
    private val controls = ToolPaintControls { toolPaint }

    @Test
    fun `forwards color changes to the active paint`() {
        controls.setColor(0xFF123456.toInt())

        verify(toolPaint).color = 0xFF123456.toInt()
    }

    @Test
    fun `forwards width and cap changes to the active paint`() {
        controls.setStrokeWidth(18)
        controls.setStrokeCap(Cap.ROUND)

        verify(toolPaint).strokeWidth = 18f
        verify(toolPaint).strokeCap = Cap.ROUND
    }
}
