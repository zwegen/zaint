package de.zwegen.zpaint.command

import android.graphics.Canvas
import de.zwegen.zpaint.contract.ZaintLayerContracts

/**
 * Ordered command group used for one logical document operation.
 *
 * The sequence prepares the canvas from the currently selected layer before every child command.
 * A child may change that selection, so the next child deliberately receives the current bitmap
 * instead of a bitmap captured at the beginning of the group.
 */
class CommandSequence {
    private val entries = mutableListOf<Command>()

    val commands: List<Command>
        get() = entries

    fun append(command: Command) {
        entries += command
    }

    fun execute(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        entries.toList().forEach { command ->
            layerModel.currentLayer?.let { canvas.setBitmap(it.bitmap) }
            command.run(canvas, layerModel)
        }
    }

    fun release() {
        entries.forEach(Command::freeResources)
    }
}
