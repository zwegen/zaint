package de.zwegen.zpaint.command

import de.zwegen.zpaint.contract.ZaintLayerContracts

/**
 * Model-only command base for layer list mutations.
 *
 * The helpers keep selection and ordering rules in one place. In particular, a failed move restores
 * the source layer instead of leaving the document with a silently removed layer.
 */
abstract class LayerModelCommand : ModelChangeCommand() {
    protected fun selectFirstLayer(layerModel: ZaintLayerContracts.Model) {
        layerModel.currentLayer = layerModel.getLayerAt(0)
    }

    protected fun moveLayer(
        layerModel: ZaintLayerContracts.Model,
        sourcePosition: Int,
        targetPosition: Int
    ): Boolean {
        if (sourcePosition == targetPosition) return true

        val layerToMove = layerModel.getLayerAt(sourcePosition) ?: return false
        if (!layerModel.removeLayerAt(sourcePosition)) return false

        if (layerModel.addLayerAt(targetPosition, layerToMove)) return true

        layerModel.addLayerAt(sourcePosition, layerToMove)
        return false
    }
}
