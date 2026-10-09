package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.LineStyleReplay
import de.zwegen.zpaint.tools.implementation.LineStyleUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class LineStyleUpdateTest {
    private val update = LineStyleUpdate()

    @Test
    fun `replays the full segment after a style change`() {
        assertEquals(LineStyleReplay.SEGMENT, update.replayFor(true, true, false, true, false))
    }

    @Test
    fun `replays the pending start point after a style change`() {
        assertEquals(LineStyleReplay.START_POINT, update.replayFor(true, false, false, true, false))
    }

    @Test
    fun `does not replay after undo or an unchanged style`() {
        assertEquals(LineStyleReplay.NONE, update.replayFor(true, false, false, true, true))
        assertEquals(LineStyleReplay.NONE, update.replayFor(true, true, false, true, false, false))
    }
}
