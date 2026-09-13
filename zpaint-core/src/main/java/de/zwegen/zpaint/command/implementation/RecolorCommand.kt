/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.FilterProcessor

class RecolorCommand(
    val sourceColor: Int,
    val targetColor: Int,
    val tolerance: Int
) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = FilterProcessor.applyRecolor(layer.bitmap, sourceColor, targetColor, tolerance)
    }

    override fun freeResources() = Unit
}
