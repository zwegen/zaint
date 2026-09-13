package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer

/** Restores one bitmap as the selected front layer of a Zaint document. */
class ZaintBitmapDocumentLoad(bitmap: Bitmap) : ZaintDocumentContentLoad() {
    private val source = ZaintLayer(bitmap)

    val loadedBitmap: Bitmap
        get() = source.bitmap

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        insertCopy(layerModel, 0, source)?.let { layerModel.currentLayer = it }
    }
}
