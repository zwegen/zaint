package de.zwegen.zpaint.command.replay

/**
 * Plans checkpoint compaction against a byte budget instead of a command-count or time limit.
 * Entries are newest-first, matching DefaultCommandManager's undo deque.
 */
class UndoHistoryByteBudget(
    private val maxBytes: Long,
    private val minimumRetainedCommands: Int
) {
    data class Entry(val id: Long, val byteCount: Long, val isReplayable: Boolean)

    data class CompactionPlan(
        val commandIdsOldestFirst: List<Long>,
        val retainedNewestIds: List<Long>
    )

    init {
        require(maxBytes > 0)
        require(minimumRetainedCommands >= 0)
    }

    fun totalBytes(entries: List<Entry>, fixedHistoryBytes: Long = 0L): Long {
        require(fixedHistoryBytes >= 0)
        return entries.fold(fixedHistoryBytes) { total, entry ->
            total.saturatingAdd(entry.byteCount.coerceAtLeast(0L))
        }
    }

    /**
     * Returns only the oldest contiguous replayable prefix. Newer entries are never included, so
     * their undo and redo behavior stays intact. A non-replayable oldest command deliberately
     * yields no plan; the caller keeps the existing history unchanged in that case.
     */
    fun planCompaction(entriesNewestFirst: List<Entry>, fixedHistoryBytes: Long = 0L): CompactionPlan? {
        if (totalBytes(entriesNewestFirst, fixedHistoryBytes) <= maxBytes ||
            entriesNewestFirst.size <= minimumRetainedCommands
        ) {
            return null
        }

        val compactableOldestFirst = entriesNewestFirst
            .drop(minimumRetainedCommands)
            .asReversed()
            .takeWhile(Entry::isReplayable)
        if (compactableOldestFirst.isEmpty()) {
            return null
        }

        return CompactionPlan(
            commandIdsOldestFirst = compactableOldestFirst.map(Entry::id),
            retainedNewestIds = entriesNewestFirst.take(minimumRetainedCommands).map(Entry::id)
        )
    }

    private fun Long.saturatingAdd(other: Long): Long =
        if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other
}
