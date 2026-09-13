package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.LayerModelCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Removes the requested layer and keeps the first remaining layer selected. */
class ZaintRemoveLayerCommand(layerIndex: Int) : LayerModelCommand() {
    private val removal = LayerRemoval(layerIndex)

    val layerIndex: Int
        get() = removal.layerIndex

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        if (layerModel.removeLayerAt(removal.layerIndex)) {
            selectFirstLayer(layerModel)
        }
    }

    private data class LayerRemoval(val layerIndex: Int)
}
