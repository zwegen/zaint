package de.zwegen.zpaint.test.command.replay

import de.zwegen.zpaint.command.replay.UndoHistoryByteBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UndoHistoryByteBudgetTest {
    @Test
    fun totalsSnapshotAndAreaBeforeAfterBitmapsByTheirAllocatedByteCounts() {
        val budget = UndoHistoryByteBudget(maxBytes = 1_000_000L, minimumRetainedCommands = 2)

        val total = budget.totalBytes(
            listOf(
                UndoHistoryByteBudget.Entry(id = 1L, byteCount = 4L * 800 * 600, isReplayable = true),
                UndoHistoryByteBudget.Entry(id = 2L, byteCount = 2L * 4 * 120 * 40, isReplayable = true)
            )
        )

        assertEquals(1_920_000L + 38_400L, total)
    }

    @Test
    fun overBudgetCompactsOnlyOldestReplayableCommandsAndRetainsNewestUndoSteps() {
        val budget = UndoHistoryByteBudget(maxBytes = 100L, minimumRetainedCommands = 2)
        val entriesNewestFirst = listOf(
            UndoHistoryByteBudget.Entry(5L, 30L, true),
            UndoHistoryByteBudget.Entry(4L, 30L, true),
            UndoHistoryByteBudget.Entry(3L, 30L, true),
            UndoHistoryByteBudget.Entry(2L, 30L, true),
            UndoHistoryByteBudget.Entry(1L, 30L, true)
        )

        val plan = budget.planCompaction(entriesNewestFirst)

        assertEquals(listOf(1L, 2L, 3L), plan!!.commandIdsOldestFirst)
        assertEquals(listOf(5L, 4L), plan.retainedNewestIds)
    }

    @Test
    fun doesNotCompactWhenAtOrBelowBudget() {
        val budget = UndoHistoryByteBudget(maxBytes = 150L, minimumRetainedCommands = 2)

        assertNull(budget.planCompaction(listOf(
            UndoHistoryByteBudget.Entry(3L, 50L, true),
            UndoHistoryByteBudget.Entry(2L, 50L, true),
            UndoHistoryByteBudget.Entry(1L, 50L, true)
        )))
    }

    @Test
    fun nonReplayableOldestPrefixIsNotDiscardedWhenBudgetIsExceeded() {
        val budget = UndoHistoryByteBudget(maxBytes = 100L, minimumRetainedCommands = 2)

        assertNull(budget.planCompaction(listOf(
            UndoHistoryByteBudget.Entry(4L, 40L, true),
            UndoHistoryByteBudget.Entry(3L, 40L, true),
            UndoHistoryByteBudget.Entry(2L, 40L, true),
            UndoHistoryByteBudget.Entry(1L, 40L, false)
        )))
    }
}
