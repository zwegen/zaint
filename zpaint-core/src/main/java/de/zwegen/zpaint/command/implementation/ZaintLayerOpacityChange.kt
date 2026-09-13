package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.ModelChangeCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Applies one explicitly requested opacity value to a layer, if it still exists. */
class ZaintLayerOpacityChange(layerIndex: Int, opacity: Int) : ModelChangeCommand() {
    private val change = OpacityChange(layerIndex, opacity)

    val layerIndex: Int
        get() = change.layerIndex

    val opacity: Int
        get() = change.opacity

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        layerModel.getLayerAt(change.layerIndex)?.opacityPercentage = change.opacity
    }

    private data class OpacityChange(val layerIndex: Int, val opacity: Int)
}
