package de.zwegen.zpaint.command

import de.zwegen.zpaint.model.CommandManagerModel
import de.zwegen.zpaint.model.LayerModelSnapshot

/**
 * Zaint's document history boundary.
 *
 * Implementations own command execution, undo/redo state and persistence snapshots; UI callers
 * only interact with this stable timeline contract.
 */
interface ZaintCommandTimeline {
    val isUndoAvailable: Boolean
    val isRedoAvailable: Boolean
    val lastExecutedCommand: Command?
    val isBusy: Boolean
    val commandManagerModel: CommandManagerModel?

    fun captureProjectSnapshot(): LayerModelSnapshot?

    fun addCommandListener(commandListener: CommandListener)

    fun removeCommandListener(commandListener: CommandListener)

    fun addCommand(command: Command?)

    fun addCommandWithoutUndo(command: Command?)

    fun setInitialStateCommand(command: Command)

    fun loadProjectHistory(model: CommandManagerModel?)

    fun undo()

    fun redo()

    fun reset()

    fun shutdown()

    fun undoIgnoringColorChanges()

    fun undoIgnoringColorChangesAndAddCommand(command: Command)

    fun undoInConnectedLinesMode()

    fun redoInConnectedLinesMode()

    fun popFirstCommandInUndo()

    fun popFirstCommandInRedo()

    fun executeAllCommands()

    fun getUndoCommandCount(): Int

    fun getColorCommandCount(): Int

    fun isLastColorCommandOnTop(): Boolean

    interface CommandListener {
        fun commandPostExecute()
    }
}
