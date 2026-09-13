package de.zwegen.zpaint.command.bitmap

import android.graphics.Bitmap
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Target dimensions for a document-wide resize operation. */
data class ResizeTarget(val width: Int, val height: Int)

interface LayerResizer {
    fun resize(layer: ZaintLayerContracts.ZaintLayer, target: ResizeTarget)
}

/** Android bitmap adapter for scaling an editable layer. */
class AndroidLayerResizer : LayerResizer {
    override fun resize(layer: ZaintLayerContracts.ZaintLayer, target: ResizeTarget) {
        layer.bitmap = Bitmap.createScaledBitmap(layer.bitmap, target.width, target.height, true)
    }
}
