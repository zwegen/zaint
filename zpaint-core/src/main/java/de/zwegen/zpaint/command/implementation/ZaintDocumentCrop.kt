package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.crop.AndroidLayerCropper
import de.zwegen.zpaint.command.crop.CropRegion
import de.zwegen.zpaint.command.crop.LayerCropper
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Crops all Zaint layers to one validated inclusive document region. */
class ZaintDocumentCrop(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    private val maximumBitmapResolution: Int,
    private val layerCropper: LayerCropper = AndroidLayerCropper()
) : Command {
    private val region = CropRegion(left, top, right, bottom)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val targetSize = region.targetSize(
            layerModel.width,
            layerModel.height,
            maximumBitmapResolution
        ) ?: return
        layerModel.listIterator(0).forEachRemaining { layerCropper.crop(it, region, targetSize) }
        layerModel.width = targetSize.width
        layerModel.height = targetSize.height
    }

    override fun freeResources() = Unit
}
