/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.FilterProcessor

class BlurAreaCommand(
    val value: Int,
    val centerX: Float,
    val centerY: Float,
    val radius: Float
) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = FilterProcessor.applyBlurArea(layer.bitmap, value, centerX, centerY, radius)
    }

    override fun freeResources() = Unit
}
