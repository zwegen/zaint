package de.zwegen.zpaint.test.command.replay

import de.zwegen.zpaint.command.replay.UndoHistoryCheckpointState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryCheckpointStateTest {
    @Test
    fun checkpointFromRunningHistoryIsRejectedAfterNewCommand() {
        val state = UndoHistoryCheckpointState()
        val candidate = state.request(historyVersion = 7, commandIds = listOf(11L, 12L), allCommandsSupported = true)!!

        assertFalse(state.canApply(candidate, historyVersion = 8, commandIds = listOf(11L, 12L)))
    }

    @Test
    fun checkpointFromRunningHistoryIsRejectedAfterUndo() {
        val state = UndoHistoryCheckpointState()
        val candidate = state.request(historyVersion = 7, commandIds = listOf(11L, 12L), allCommandsSupported = true)!!

        assertFalse(state.canApply(candidate, historyVersion = 8, commandIds = listOf(11L)))
    }

    @Test
    fun checkpointFromRunningHistoryIsRejectedAfterRedo() {
        val state = UndoHistoryCheckpointState()
        val candidate = state.request(historyVersion = 7, commandIds = listOf(11L, 12L), allCommandsSupported = true)!!

        assertFalse(state.canApply(candidate, historyVersion = 8, commandIds = listOf(11L, 12L)))
    }

    @Test
    fun checkpointFromRunningHistoryIsRejectedAfterBranch() {
        val state = UndoHistoryCheckpointState()
        val candidate = state.request(historyVersion = 7, commandIds = listOf(11L, 12L), allCommandsSupported = true)!!

        assertFalse(state.canApply(candidate, historyVersion = 9, commandIds = listOf(11L, 13L)))
    }

    @Test
    fun unsupportedCommandPrefixDoesNotCreateCheckpointRequest() {
        val state = UndoHistoryCheckpointState()

        assertNull(state.request(historyVersion = 7, commandIds = listOf(11L, 12L), allCommandsSupported = false))
    }

    @Test
    fun unchangedConcreteHistoryAcceptsItsCheckpoint() {
        val state = UndoHistoryCheckpointState()
        val candidate = state.request(historyVersion = 7, commandIds = listOf(11L, 12L), allCommandsSupported = true)!!

        assertTrue(state.canApply(candidate, historyVersion = 7, commandIds = listOf(11L, 12L)))
    }
}
