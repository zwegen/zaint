/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.FilterArea
import de.zwegen.zpaint.tools.implementation.FilterProcessor
import de.zwegen.zpaint.tools.implementation.FilterType

class AreaFilterCommand(
    val filterType: FilterType,
    val value: Int,
    val area: FilterArea
) : Command {
    constructor(filterType: FilterType, value: Int, centerX: Float, centerY: Float, radius: Float) :
        this(filterType, value, FilterArea.circle(centerX, centerY, radius))

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = FilterProcessor.applyArea(layer.bitmap, filterType, value, area)
    }

    override fun freeResources() = Unit
}
