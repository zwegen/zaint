package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Restores a complete Zaint layer stack while retaining layer presentation state. */
class ZaintLayerStackDocumentLoad(layers: List<ZaintLayerContracts.ZaintLayer>) : ZaintDocumentContentLoad() {
    private val sourceLayers = layers.toList()

    val loadedLayers: List<ZaintLayerContracts.ZaintLayer>
        get() = sourceLayers

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        sourceLayers.forEachIndexed { index, layer -> insertCopy(layerModel, index, layer) }
        layerModel.currentLayer = layerModel.getLayerAt(0)
    }
}
