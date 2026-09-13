package de.zwegen.zpaint.command.replay

import android.graphics.Bitmap
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintClipboardStamp
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.implementation.ZaintBitmapDocumentLoad
import de.zwegen.zpaint.command.implementation.ZaintLayerStackDocumentLoad
import de.zwegen.zpaint.command.implementation.ZaintSmudgeStroke
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Counts native bitmap allocations retained exclusively by undo/redo commands. Bitmap dimensions
 * are not an estimate: [Bitmap.allocationByteCount] includes the actual row stride and format.
 */
object UndoHistoryMemoryEstimator {
    fun allocatedByteCount(commands: Iterable<Command>): Long {
        val seen = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())
        commands.forEach { collect(it, seen) }
        return seen.fold(0L) { total, bitmap -> total.saturatingAdd(bitmap.allocationByteCount.toLong()) }
    }

    private fun collect(command: Command, seen: MutableSet<Bitmap>) {
        when (command) {
            is ZaintClipboardStamp -> command.bitmap?.let(seen::add)
            is ZaintCommandBatch -> command.commands.forEach { collect(it, seen) }
            is ZaintBitmapDocumentLoad -> seen.add(command.loadedBitmap)
            is ZaintLayerStackDocumentLoad -> command.loadedLayers.forEach { seen.add(it.bitmap) }
            is ZaintSmudgeStroke -> seen.add(command.originalBitmap)
        }
    }

    private fun Long.saturatingAdd(other: Long): Long =
        if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other
}
