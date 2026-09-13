package de.zwegen.zpaint.command.color

/** Keeps the line-tool color undo sequence independent from Android and tool implementations. */
class ColorChangeSequence(initialFirstTime: Boolean = true) {
    var firstTime: Boolean = initialFirstTime

    fun nextAction(isLineTool: Boolean): PaintColorAction {
        if (!isLineTool) return PaintColorAction.SET_COLOR
        if (firstTime) {
            firstTime = false
            return PaintColorAction.SET_COLOR
        }
        return PaintColorAction.RESTORE_LINE_COLOR
    }
}

enum class PaintColorAction { SET_COLOR, RESTORE_LINE_COLOR }
