package de.zwegen.zpaint.command.implementation

import android.graphics.Paint
import android.graphics.Point
import de.zwegen.zpaint.command.ModelChangeCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.helper.FillAlgorithmFactory

/** Applies one immutable Zaint flood-fill request to the active layer. */
class ZaintFloodFill(
    private val fillAlgorithmFactory: FillAlgorithmFactory,
    clickedPixel: Point,
    paint: Paint,
    colorTolerance: Float
) : ModelChangeCommand() {
    private val request = FillRequest(clickedPixel, paint, colorTolerance)

    val clickedPixel: Point
        get() = request.position

    val paint: Paint
        get() = request.paint

    val colorTolerance: Float
        get() = request.tolerance

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        val bitmap = layerModel.currentLayer?.bitmap ?: return
        val position = request.position
        val fillAlgorithm = fillAlgorithmFactory.createFillAlgorithm()
        fillAlgorithm.setParameters(
            bitmap,
            position,
            request.paint.color,
            bitmap.getPixel(position.x, position.y),
            request.tolerance
        )
        fillAlgorithm.performFilling()
    }

    private data class FillRequest(val position: Point, val paint: Paint, val tolerance: Float)
}
