package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.implementation.ZaintColorChange
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ColorChangedCommandTest {
    @Test
    fun updatesColorThroughTheApplicationBoundary() {
        val tool = Mockito.mock(Tool::class.java)
        val target = RecordingColorChangeTarget()
        val color = 0xFF336699.toInt()

        ZaintColorChange(tool, target, color).run(
            Mockito.mock(Canvas::class.java),
            Mockito.mock(ZaintLayerContracts.Model::class.java)
        )

        Mockito.verify(tool).changePaintColor(color)
        assertTrue(target.executed)
        assertEquals(color, target.indicatorColor)
    }

    private class RecordingColorChangeTarget : ColorChangeTarget {
        var executed = false
        var indicatorColor: Int? = null

        override fun runColorChange(block: () -> Unit) {
            executed = true
            block()
        }

        override fun updateColorIndicator(color: Int) {
            indicatorColor = color
        }
    }
}
