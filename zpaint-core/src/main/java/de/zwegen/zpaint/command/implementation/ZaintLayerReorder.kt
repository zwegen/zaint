package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.LayerModelCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Moves a layer while preserving the document if the requested destination is invalid. */
class ZaintLayerReorder(fromIndex: Int, toIndex: Int) : LayerModelCommand() {
    private val move = LayerMove(fromIndex, toIndex)

    val fromIndex: Int
        get() = move.fromIndex

    val toIndex: Int
        get() = move.toIndex

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        moveLayer(layerModel, move.fromIndex, move.toIndex)
    }

    private data class LayerMove(val fromIndex: Int, val toIndex: Int)
}
