package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.ZaintCommandTimeline.CommandListener
import de.zwegen.zpaint.command.replay.LayerModelSnapshotCommandExecutor
import de.zwegen.zpaint.command.replay.SnapshotReplayCommandPolicy
import de.zwegen.zpaint.command.replay.UndoHistoryByteBudget
import de.zwegen.zpaint.command.replay.UndoHistoryMemoryEstimator
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.CommandManagerModel
import de.zwegen.zpaint.model.LayerModelSnapshot
import java.util.ArrayDeque
import java.util.Deque

private const val UNDO_HISTORY_BYTE_BUDGET = 48L * 1024L * 1024L
private const val MINIMUM_RETAINED_UNDO_COMMANDS = 5

/**
 * Owns the editable command timeline for one image.
 *
 * Commands are stored newest first. Undo moves the newest edit to the redo timeline and restores
 * the image by replaying the remaining edits from a known base state. Redo applies one stored edit
 * again. Replaying instead of attempting inverse drawing operations keeps bitmap, layer, crop and
 * transformation commands deterministic.
 */
class ZaintHistoryManager(
    private val commonFactory: CommonFactory,
    private val layerModel: ZaintLayerContracts.Model
) : ZaintCommandTimeline {
    private val listeners = mutableListOf<CommandListener>()
    private val undoHistory: Deque<Command> = ArrayDeque()
    private val redoHistory: Deque<Command> = ArrayDeque()
    private val historyBudget = UndoHistoryByteBudget(
        maxBytes = UNDO_HISTORY_BYTE_BUDGET,
        minimumRetainedCommands = MINIMUM_RETAINED_UNDO_COMMANDS
    )

    private var baseCommand: Command? = null
    private var baseSnapshot: LayerModelSnapshot? = null
    private var undoDisabledForMemory = false
    private var nextHistoryId = 0L
    private val historyIds = java.util.IdentityHashMap<Command, Long>()

    override val isBusy: Boolean = false

    override val lastExecutedCommand: Command?
        get() = undoHistory.firstOrNull()

    override val isUndoAvailable: Boolean
        get() = !undoDisabledForMemory && undoHistory.any { it !is ZaintColorChange }

    override val isRedoAvailable: Boolean
        get() = !undoDisabledForMemory && redoHistory.any { it !is ZaintColorChange }

    override val commandManagerModel: CommandManagerModel?
        get() {
            val initial = baseCommand ?: return null
            val persistedCommands = ArrayList<Command>(undoHistory.size + redoHistory.size)
            val redoIterator = redoHistory.descendingIterator()
            while (redoIterator.hasNext()) {
                persistedCommands.add(redoIterator.next())
            }
            persistedCommands.addAll(undoHistory)
            return CommandManagerModel(initial, persistedCommands)
        }

    override fun captureProjectSnapshot(): LayerModelSnapshot? = captureCurrentModel()

    override fun addCommandListener(commandListener: CommandListener) {
        listeners.add(commandListener)
    }

    override fun removeCommandListener(commandListener: CommandListener) {
        listeners.remove(commandListener)
    }

    override fun addCommand(command: Command?) {
        clearRedoHistory()
        if (command != null && !undoDisabledForMemory) {
            undoHistory.addFirst(command)
            historyId(command)
        }
        run(command)
        compactUndoHistoryIfNeeded()
        notifyListeners()
    }

    override fun addCommandWithoutUndo(command: Command?) {
        run(command)
        notifyListeners()
    }

    override fun setInitialStateCommand(command: Command) {
        baseSnapshot?.release()
        baseSnapshot = null
        baseCommand = command
        undoDisabledForMemory = false
    }

    override fun loadProjectHistory(model: CommandManagerModel?) {
        model ?: return
        setInitialStateCommand(model.initialCommand)
        reset()
        model.commands.forEach(::addCommand)
    }

    override fun undo() {
        discardLeadingColorChanges(undoHistory)
        val command = undoHistory.pollFirst() ?: return
        redoHistory.addFirst(command)
        rebuildImage()
        notifyListeners()
    }

    override fun redo() {
        discardLeadingColorChanges(redoHistory)
        val command = redoHistory.pollFirst() ?: return
        undoHistory.addFirst(command)
        run(command)
        notifyListeners()
    }

    override fun reset() {
        releaseHistory(undoHistory)
        releaseHistory(redoHistory)
        undoHistory.clear()
        redoHistory.clear()
        layerModel.reset()
        baseCommand?.let(::run)
        replaceBaseSnapshot(captureCurrentModel())
        undoDisabledForMemory = false
        notifyListeners()
    }

    override fun shutdown() {
        releaseHistory(undoHistory)
        releaseHistory(redoHistory)
        undoHistory.clear()
        redoHistory.clear()
        baseSnapshot?.release()
        baseSnapshot = null
        listeners.clear()
    }

    override fun undoIgnoringColorChanges() {
        val colors = takeLeadingColorChanges(undoHistory)
        val command = undoHistory.pollFirst()
        if (command != null) {
            redoHistory.addFirst(command)
            rebuildImage()
        }
        restoreColorChanges(colors)
        notifyListeners()
    }

    override fun undoIgnoringColorChangesAndAddCommand(command: Command) {
        val colors = takeLeadingColorChanges(undoHistory)
        val replaced = undoHistory.pollFirst()
        if (replaced != null) {
            redoHistory.addFirst(replaced)
            rebuildImage()
        }
        clearRedoHistory()
        undoHistory.addFirst(command)
        historyId(command)
        run(command)
        restoreColorChanges(colors)
        compactUndoHistoryIfNeeded()
        notifyListeners()
    }

    override fun undoInConnectedLinesMode() {
        val colors = takeLeadingColorChanges(undoHistory)
        val removed = if (colors.isNotEmpty()) colors.removeFirst() else undoHistory.pollFirst()
        if (removed != null) {
            redoHistory.addFirst(removed)
            rebuildImage()
        }
        restoreColorChanges(colors)
        notifyListeners()
    }

    override fun redoInConnectedLinesMode() {
        redo()
    }

    override fun popFirstCommandInUndo() {
        undoHistory.pollFirst()?.freeResources()
    }

    override fun popFirstCommandInRedo() {
        redoHistory.pollFirst()?.freeResources()
    }

    override fun executeAllCommands() {
        rebuildImage()
        notifyListeners()
    }

    override fun getUndoCommandCount(): Int = undoHistory.size

    override fun getColorCommandCount(): Int = undoHistory.count { it is ZaintColorChange }

    override fun isLastColorCommandOnTop(): Boolean =
        undoHistory.firstOrNull() is ZaintColorChange && getColorCommandCount() == 1

    private fun run(command: Command?) {
        command ?: return
        val canvas = commonFactory.createCanvas()
        canvas.setBitmap(layerModel.currentLayer?.bitmap)
        command.run(canvas, layerModel)
        canvas.setBitmap(null)
    }

    private fun rebuildImage() {
        val layerPresentation = layerModel.layers.map { it.isVisible to it.opacityPercentage }
        layerModel.reset()
        baseCommand?.let(::run)
        val undoIterator = undoHistory.descendingIterator()
        while (undoIterator.hasNext()) {
            run(undoIterator.next())
        }
        layerModel.layers.forEachIndexed { index, layer ->
            layerPresentation.getOrNull(index)?.let { state ->
                layer.isVisible = state.first
                layer.opacityPercentage = state.second
            }
        }
    }

    private fun clearRedoHistory() {
        releaseHistory(redoHistory)
        redoHistory.clear()
    }

    private fun discardLeadingColorChanges(history: Deque<Command>) {
        while (history.firstOrNull() is ZaintColorChange) {
            history.removeFirst().freeResources()
        }
    }

    private fun takeLeadingColorChanges(history: Deque<Command>): Deque<Command> {
        val colors: Deque<Command> = ArrayDeque()
        while (history.firstOrNull() is ZaintColorChange) {
            colors.addLast(history.removeFirst())
        }
        return colors
    }

    private fun restoreColorChanges(colors: Deque<Command>) {
        val colorIterator = colors.descendingIterator()
        while (colorIterator.hasNext()) {
            val colorCommand = colorIterator.next()
            undoHistory.addFirst(colorCommand)
            run(colorCommand)
        }
    }

    private fun compactUndoHistoryIfNeeded() {
        val snapshot = baseSnapshot ?: return
        if (undoHistory.size <= MINIMUM_RETAINED_UNDO_COMMANDS) return
        val entries = undoHistory.map { command ->
            UndoHistoryByteBudget.Entry(
                id = historyId(command),
                byteCount = UndoHistoryMemoryEstimator.allocatedByteCount(listOf(command)),
                isReplayable = SnapshotReplayCommandPolicy.supports(command.javaClass)
            )
        }
        val plan = historyBudget.planCompaction(
            entries,
            snapshot.allocatedByteCount() + UndoHistoryMemoryEstimator.allocatedByteCount(listOfNotNull(baseCommand))
        ) ?: return
        val commands = undoHistory.toList().takeLast(plan.commandIdsOldestFirst.size).asReversed()
        val replayStart = snapshot.copy()
        val checkpoint = try {
            LayerModelSnapshotCommandExecutor(commonFactory).execute(replayStart, commands)
        } catch (_: Throwable) {
            return
        } finally {
            replayStart.release()
        }
        baseCommand = snapshotCommand(checkpoint)
        replaceBaseSnapshot(checkpoint)
        repeat(commands.size) { undoHistory.removeLast().freeResources() }
    }

    private fun snapshotCommand(snapshot: LayerModelSnapshot): Command = ZaintCommandBatch().apply {
        addCommand(ZaintDocumentDimensions(snapshot.width, snapshot.height))
        addCommand(ZaintLayerStackDocumentLoad(snapshot.copyLayers()))
    }

    private fun replaceBaseSnapshot(snapshot: LayerModelSnapshot?) {
        baseSnapshot?.release()
        baseSnapshot = snapshot
    }

    private fun captureCurrentModel(): LayerModelSnapshot? = try {
        if (layerModel.layerCount == 0) null else LayerModelSnapshot.capture(layerModel)
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun historyId(command: Command): Long =
        historyIds.getOrPut(command) { ++nextHistoryId }

    private fun releaseHistory(history: Iterable<Command>) {
        history.forEach(Command::freeResources)
    }

    private fun notifyListeners() {
        listeners.toList().forEach(CommandListener::commandPostExecute)
    }
}
