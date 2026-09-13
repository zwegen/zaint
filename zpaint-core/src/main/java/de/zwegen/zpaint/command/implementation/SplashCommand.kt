package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.FilterProcessor

/** Keeps the selected colours while converting all other pixels of the active layer to grey. */
class SplashCommand(
    selectedColors: IntArray,
    tolerances: IntArray
) : Command {
    private val selectedColors = selectedColors.copyOf()
    private val tolerances = tolerances.copyOf()

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val layer = layerModel.currentLayer ?: return
        layer.bitmap = FilterProcessor.applySplash(layer.bitmap, selectedColors, tolerances)
    }

    override fun freeResources() = Unit
}
