package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.ZaintCommandTimeline.CommandListener
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.CommandManagerModel
import de.zwegen.zpaint.model.LayerModelSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes editing operations away from the UI thread.
 *
 * The wrapped manager owns history and bitmap state. This class owns scheduling only: every
 * operation obtains the same mutex, works against the layer model under its monitor, then informs
 * UI listeners on the main thread after the operation has settled.
 */
open class ZaintHistoryDispatcher(
    private val commandManager: ZaintCommandTimeline,
    private val layerModel: ZaintLayerContracts.Model
) : ZaintCommandTimeline {
    private val listeners = mutableListOf<CommandListener>()
    private val operationMutex = Mutex()
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var acceptingOperations = true

    override val isBusy: Boolean
        get() = operationMutex.isLocked

    override val lastExecutedCommand: Command?
        get() = commandManager.lastExecutedCommand

    override val commandManagerModel: CommandManagerModel?
        get() = synchronized(layerModel) { commandManager.commandManagerModel }

    override fun captureProjectSnapshot(): LayerModelSnapshot? =
        synchronized(layerModel) { commandManager.captureProjectSnapshot() }

    override val isUndoAvailable: Boolean
        get() = synchronized(layerModel) { commandManager.isUndoAvailable }

    override val isRedoAvailable: Boolean
        get() = synchronized(layerModel) { commandManager.isRedoAvailable }

    override fun addCommandListener(commandListener: CommandListener) {
        listeners.add(commandListener)
    }

    override fun removeCommandListener(commandListener: CommandListener) {
        listeners.remove(commandListener)
    }

    override fun addCommand(command: Command?) = submit {
        commandManager.addCommand(command)
    }

    override fun addCommandWithoutUndo(command: Command?) = submit {
        commandManager.addCommandWithoutUndo(command)
    }

    override fun setInitialStateCommand(command: Command) {
        synchronized(layerModel) { commandManager.setInitialStateCommand(command) }
    }

    override fun loadProjectHistory(model: CommandManagerModel?) = submit {
        commandManager.loadProjectHistory(model)
    }

    override fun undo() = submitIf(ZaintCommandTimeline::isUndoAvailable) {
        commandManager.undo()
    }

    override fun redo() = submitIf(ZaintCommandTimeline::isRedoAvailable) {
        commandManager.redo()
    }

    override fun reset() {
        synchronized(layerModel) { commandManager.reset() }
        notifyListeners()
    }

    override fun shutdown() {
        acceptingOperations = false
        workerScope.cancel()
        synchronized(layerModel) { commandManager.shutdown() }
    }

    override fun undoIgnoringColorChanges() = submitIf(ZaintCommandTimeline::isUndoAvailable) {
        commandManager.undoIgnoringColorChanges()
    }

    override fun undoIgnoringColorChangesAndAddCommand(command: Command) = submitIf(
        ZaintCommandTimeline::isUndoAvailable
    ) {
        commandManager.undoIgnoringColorChangesAndAddCommand(command)
    }

    override fun undoInConnectedLinesMode() = submitIf(ZaintCommandTimeline::isUndoAvailable) {
        commandManager.undoInConnectedLinesMode()
    }

    override fun redoInConnectedLinesMode() = submitIf(ZaintCommandTimeline::isRedoAvailable) {
        commandManager.redoInConnectedLinesMode()
    }

    override fun popFirstCommandInUndo() {
        synchronized(layerModel) { commandManager.popFirstCommandInUndo() }
    }

    override fun popFirstCommandInRedo() {
        synchronized(layerModel) { commandManager.popFirstCommandInRedo() }
    }

    override fun executeAllCommands() = submit {
        commandManager.executeAllCommands()
    }

    override fun getUndoCommandCount(): Int =
        synchronized(layerModel) { commandManager.getUndoCommandCount() }

    override fun getColorCommandCount(): Int =
        synchronized(layerModel) { commandManager.getColorCommandCount() }

    override fun isLastColorCommandOnTop(): Boolean =
        synchronized(layerModel) { commandManager.isLastColorCommandOnTop() }

    private fun submit(work: () -> Unit) {
        workerScope.launch {
            operationMutex.withLock {
                if (acceptingOperations) {
                    synchronized(layerModel) { work() }
                }
                publishCompletion()
            }
        }
    }

    private fun submitIf(condition: ZaintCommandTimeline.() -> Boolean, work: () -> Unit) {
        workerScope.launch {
            operationMutex.withLock {
                if (acceptingOperations) {
                    synchronized(layerModel) {
                        if (commandManager.condition()) {
                            work()
                        }
                    }
                }
                publishCompletion()
            }
        }
    }

    private suspend fun publishCompletion() {
        withContext(Dispatchers.Main) { notifyListeners() }
    }

    private fun notifyListeners() {
        if (!acceptingOperations) return
        listeners.toList().forEach(CommandListener::commandPostExecute)
    }
}
