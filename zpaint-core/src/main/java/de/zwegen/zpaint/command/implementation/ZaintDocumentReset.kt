package de.zwegen.zpaint.command.implementation

import de.zwegen.zpaint.command.ModelChangeCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Clears the editable document before Zaint creates its fresh base layer. */
class ZaintDocumentReset : ModelChangeCommand() {
    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        layerModel.reset()
    }
}
