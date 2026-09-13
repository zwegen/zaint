package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import de.zwegen.zpaint.command.ModelChangeCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer

/** Shared insertion boundary for bitmap-backed Zaint document loading. */
abstract class ZaintDocumentContentLoad : ModelChangeCommand() {
    protected fun insertCopy(
        layerModel: ZaintLayerContracts.Model,
        position: Int,
        source: ZaintLayerContracts.ZaintLayer
    ): ZaintLayer? {
        val layer = ZaintLayer(source.bitmap.copy(Bitmap.Config.ARGB_8888, true)).also { copy ->
            copy.opacityPercentage = source.opacityPercentage
            copy.isVisible = source.isVisible
        }
        return if (layerModel.addLayerAt(position, layer)) {
            layer
        } else {
            if (!layer.bitmap.isRecycled) layer.bitmap.recycle()
            null
        }
    }
}
