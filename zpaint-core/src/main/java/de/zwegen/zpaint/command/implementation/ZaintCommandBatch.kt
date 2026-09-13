package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.CommandSequence
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Executes one ordered Zaint document operation with a fresh active-layer canvas per step. */
class ZaintCommandBatch : Command {
    private val sequence = CommandSequence()

    val commands: List<Command>
        get() = sequence.commands

    fun append(command: Command) {
        sequence.append(command)
    }

    fun addCommand(command: Command) = append(command)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) = sequence.execute(canvas, layerModel)

    override fun freeResources() = sequence.release()
}
