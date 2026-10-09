package de.zwegen.zpaint.test.command.color

import de.zwegen.zpaint.command.color.ColorChangeSequence
import de.zwegen.zpaint.command.color.PaintColorAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorChangeSequenceTest {
    @Test
    fun `normal tools always set their color`() {
        val sequence = ColorChangeSequence()

        assertEquals(PaintColorAction.SET_COLOR, sequence.nextAction(isLineTool = false))
        assertEquals(PaintColorAction.SET_COLOR, sequence.nextAction(isLineTool = false))
        assertEquals(true, sequence.firstTime)
    }

    @Test
    fun `line tools set once then restore their previous color`() {
        val sequence = ColorChangeSequence()

        assertEquals(PaintColorAction.SET_COLOR, sequence.nextAction(isLineTool = true))
        assertEquals(PaintColorAction.RESTORE_LINE_COLOR, sequence.nextAction(isLineTool = true))
        assertEquals(false, sequence.firstTime)
    }
}
