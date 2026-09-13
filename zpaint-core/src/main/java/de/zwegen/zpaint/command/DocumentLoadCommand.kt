package de.zwegen.zpaint.command

import android.graphics.Bitmap
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer

/**
 * Model-only base for commands that restore bitmap-backed document content.
 *
 * The input layers are never inserted directly: every bitmap is copied so that an undo snapshot
 * and the editable document do not accidentally share mutable pixels.
 */
abstract class DocumentLoadCommand : ModelChangeCommand() {
    protected fun copyLayer(source: ZaintLayerContracts.ZaintLayer): ZaintLayer =
        ZaintLayer(source.bitmap.copy(Bitmap.Config.ARGB_8888, true)).also { copy ->
            copy.opacityPercentage = source.opacityPercentage
            copy.isVisible = source.isVisible
        }

    protected fun addCopiedLayer(
        layerModel: ZaintLayerContracts.Model,
        position: Int,
        source: ZaintLayerContracts.ZaintLayer
    ): ZaintLayer? {
        val copy = copyLayer(source)
        return copy.takeIf { layerModel.addLayerAt(position, it) }
    }
}
