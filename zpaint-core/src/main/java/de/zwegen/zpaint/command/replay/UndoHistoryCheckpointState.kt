package de.zwegen.zpaint.command.replay

/**
 * Immutable identity guard for one asynchronous undo-history checkpoint request.
 *
 * The manager creates a request only after it has checked the replay policy for the complete
 * contiguous prefix. A completion may replace history only when both its monotonic history version
 * and the exact command identities still match.
 */
class UndoHistoryCheckpointState {
    data class Candidate internal constructor(
        internal val historyVersion: Long,
        internal val commandIds: List<Long>
    )

    fun request(
        historyVersion: Long,
        commandIds: List<Long>,
        allCommandsSupported: Boolean
    ): Candidate? {
        if (!allCommandsSupported || commandIds.isEmpty()) {
            return null
        }
        return Candidate(historyVersion, commandIds.toList())
    }

    fun canApply(candidate: Candidate, historyVersion: Long, commandIds: List<Long>): Boolean =
        candidate.historyVersion == historyVersion && candidate.commandIds == commandIds
}
