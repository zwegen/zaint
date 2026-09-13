package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.ModelChangeCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Stores the drawing-area dimensions as one document-state transition. */
class ZaintDocumentDimensions(width: Int, height: Int) : ModelChangeCommand() {
    private val canvas = CanvasSize(width, height)

    val width: Int
        get() = canvas.width

    val height: Int
        get() = canvas.height

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        layerModel.width = canvas.width
        layerModel.height = canvas.height
    }

    private data class CanvasSize(val width: Int, val height: Int)
}
