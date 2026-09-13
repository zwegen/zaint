package de.zwegen.zpaint.tools.implementation

enum class LineStyleReplay {
    SEGMENT,
    START_POINT,
    NONE
}

/** Decides which in-progress line drawing must be replayed after a style change. */
class LineStyleUpdate {
    fun replayFor(
        hasStartPoint: Boolean,
        hasEndPoint: Boolean,
        isFinalized: Boolean,
        isUndoAvailable: Boolean,
        undoRecentlyClicked: Boolean,
        styleChanged: Boolean = true
    ): LineStyleReplay = when {
        !styleChanged || !isUndoAvailable -> LineStyleReplay.NONE
        hasStartPoint && hasEndPoint -> LineStyleReplay.SEGMENT
        hasStartPoint && !isFinalized && !undoRecentlyClicked -> LineStyleReplay.START_POINT
        else -> LineStyleReplay.NONE
    }
}
