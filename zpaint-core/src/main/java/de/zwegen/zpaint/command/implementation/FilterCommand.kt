/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.FilterProcessor
import de.zwegen.zpaint.tools.implementation.FilterType

class FilterCommand(
    val filterType: FilterType,
    val value: Int
) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = FilterProcessor.apply(layer.bitmap, filterType, value)
    }

    override fun freeResources() = Unit
}
