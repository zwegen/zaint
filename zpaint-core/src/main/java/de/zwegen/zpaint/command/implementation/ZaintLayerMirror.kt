package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.bitmap.AndroidLayerMirror
import de.zwegen.zpaint.command.bitmap.LayerMirror
import de.zwegen.zpaint.command.bitmap.MirrorPlan
import de.zwegen.zpaint.command.bitmap.MirrorPlans
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Mirrors either the selected Zaint layer or every layer around a document axis. */
class ZaintLayerMirror(
    direction: ZaintMirrorDirection,
    currentLayerOnly: Boolean = true,
    private val layerMirror: LayerMirror = AndroidLayerMirror()
) : Command {
    private val request = MirrorRequest(direction, currentLayerOnly)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val plan = request.planFor(layerModel.width, layerModel.height)
        val layers = if (request.currentLayerOnly) listOfNotNull(layerModel.currentLayer) else layerModel.layers
        layers.forEach { layerMirror.mirror(it, plan) }
    }

    override fun freeResources() = Unit

    private data class MirrorRequest(
        val direction: ZaintMirrorDirection,
        val currentLayerOnly: Boolean
    ) {
        fun planFor(width: Int, height: Int): MirrorPlan = when (direction) {
            ZaintMirrorDirection.HORIZONTAL -> MirrorPlans.horizontalLine(height)
            ZaintMirrorDirection.VERTICAL -> MirrorPlans.verticalLine(width)
        }
    }
}
