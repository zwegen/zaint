package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.ModelChangeCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Selects an existing layer; an outdated selection request leaves the current layer unchanged. */
class ZaintSelectLayerCommand(layerIndex: Int) : ModelChangeCommand() {
    private val selection = LayerSelection(layerIndex)

    val layerIndex: Int
        get() = selection.layerIndex

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        layerModel.getLayerAt(selection.layerIndex)?.let { layerModel.currentLayer = it }
    }

    private data class LayerSelection(val layerIndex: Int)
}
