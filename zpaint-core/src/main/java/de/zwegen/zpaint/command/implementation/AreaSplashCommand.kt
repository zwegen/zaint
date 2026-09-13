package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.FilterArea
import de.zwegen.zpaint.tools.implementation.FilterProcessor

/** Applies each Splash colour only within its own selected filter area. */
class AreaSplashCommand(
    selectedColors: IntArray,
    tolerances: IntArray,
    areas: List<FilterArea>
) : Command {
    private val selectedColors = selectedColors.copyOf()
    private val tolerances = tolerances.copyOf()
    private val areas = areas.toList()

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = FilterProcessor.applySplashAreas(layer.bitmap, selectedColors, tolerances, areas)
    }

    override fun freeResources() = Unit
}
