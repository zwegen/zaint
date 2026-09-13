/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer

class ZaintDuplicateLayerCommand(val position: Int) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val sourceLayer = layerModel.getLayerAt(position) ?: return
        val duplicate = ZaintLayer(sourceLayer.bitmap.copy(Bitmap.Config.ARGB_8888, true)).apply {
            isVisible = sourceLayer.isVisible
            opacityPercentage = sourceLayer.opacityPercentage
        }
        layerModel.addLayerAt(position, duplicate)
        layerModel.currentLayer = duplicate
    }

    override fun freeResources() = Unit
}
