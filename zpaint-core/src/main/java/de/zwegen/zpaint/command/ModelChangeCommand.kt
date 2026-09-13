package de.zwegen.zpaint.command

import android.graphics.Canvas
import de.zwegen.zpaint.contract.ZaintLayerContracts

/**
 * Base command for changes that only affect the document model.
 *
 * These commands deliberately ignore the drawing canvas and do not own bitmaps or other resources.
 * Keeping that boundary explicit makes model-only commands usable by the normal undo flow and by
 * isolated document replay without duplicating lifecycle code.
 */
abstract class ModelChangeCommand : Command {
    final override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        applyTo(layerModel)
    }

    final override fun freeResources() = Unit

    protected abstract fun applyTo(layerModel: ZaintLayerContracts.Model)
}
