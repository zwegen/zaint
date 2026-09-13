package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import de.zwegen.zpaint.command.LayerModelCommand
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer

/** Creates and selects one new transparent layer at the front of the Zaint document. */
class ZaintAddLayerCommand(private val commonFactory: CommonFactory) : LayerModelCommand() {
    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        val dimensions = LayerDimensions(layerModel.width, layerModel.height)
        val layer = ZaintLayer(commonFactory.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888))
        if (layerModel.addLayerAt(0, layer)) {
            layerModel.currentLayer = layer
        } else if (!layer.bitmap.isRecycled) {
            layer.bitmap.recycle()
        }
    }

    private data class LayerDimensions(val width: Int, val height: Int)
}
