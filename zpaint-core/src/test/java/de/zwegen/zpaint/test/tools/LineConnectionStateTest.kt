package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.LineConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineConnectionStateTest {
    @Test
    fun `starting a connected line clears an older undo marker`() {
        val state = LineConnectionState()
        state.markUndoRequested()

        state.begin()

        assertTrue(state.isActive)
        assertFalse(state.undoRecentlyClicked)
    }

    @Test
    fun `finishing a connected line deactivates only the connection`() {
        val state = LineConnectionState()
        state.begin()
        state.markUndoRequested()

        state.finish()

        assertFalse(state.isActive)
        assertTrue(state.undoRecentlyClicked)
    }
}
