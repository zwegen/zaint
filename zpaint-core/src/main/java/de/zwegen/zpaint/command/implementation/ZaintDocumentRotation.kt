package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.bitmap.AndroidLayerRotator
import de.zwegen.zpaint.command.bitmap.AndroidCurrentLayerCanvasRotator
import de.zwegen.zpaint.command.bitmap.CurrentLayerCanvasRotator
import de.zwegen.zpaint.command.bitmap.LayerRotator
import de.zwegen.zpaint.command.bitmap.QuarterTurnPlan
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Rotates either the current Zaint layer or the complete document by one quarter turn. */
class ZaintDocumentRotation(
    direction: ZaintRotationDirection,
    currentLayerOnly: Boolean = false,
    private val layerRotator: LayerRotator = AndroidLayerRotator(),
    private val currentLayerCanvasRotator: CurrentLayerCanvasRotator = AndroidCurrentLayerCanvasRotator()
) : Command {
    private val request = RotationRequest(direction, currentLayerOnly)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        if (request.currentLayerOnly) {
            currentLayerCanvasRotator.rotateAndExpandCanvas(layerModel, request.plan.angleDegrees)
            return
        }

        layerModel.listIterator(0).forEachRemaining { layer ->
            layerRotator.rotateDocumentLayer(layer, layerModel.width, layerModel.height, request.plan)
        }
        request.plan.rotatedDocumentSize(layerModel.width, layerModel.height).let { size ->
            layerModel.width = size.width
            layerModel.height = size.height
        }
    }

    override fun freeResources() = Unit

    private data class RotationRequest(
        val direction: ZaintRotationDirection,
        val currentLayerOnly: Boolean
    ) {
        val plan: QuarterTurnPlan
            get() = QuarterTurnPlan(if (direction == ZaintRotationDirection.RIGHT) 90f else -90f)
    }
}
