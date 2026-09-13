package de.zwegen.zpaint.tools.implementation

/** Keeps the interaction state for continuing a line with the plus button. */
class LineConnectionState {
    var isActive: Boolean = false
        private set

    var undoRecentlyClicked: Boolean = false
        private set

    fun begin() {
        isActive = true
        undoRecentlyClicked = false
    }

    fun finish() {
        isActive = false
    }

    fun markUndoRequested() {
        undoRecentlyClicked = true
    }

    fun clearUndoRequest() {
        undoRecentlyClicked = false
    }
}
