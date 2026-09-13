package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.bitmap.AndroidLayerResizer
import de.zwegen.zpaint.command.bitmap.LayerResizer
import de.zwegen.zpaint.command.bitmap.ResizeTarget
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Scales all Zaint layers to one requested document size. */
class ZaintDocumentResize(
    width: Int,
    height: Int,
    private val layerResizer: LayerResizer = AndroidLayerResizer()
) : Command {
    private val target = ResizeTarget(width, height)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        layerModel.listIterator(0).forEachRemaining { layerResizer.resize(it, target) }
        layerModel.width = target.width
        layerModel.height = target.height
    }

    override fun freeResources() = Unit
}
