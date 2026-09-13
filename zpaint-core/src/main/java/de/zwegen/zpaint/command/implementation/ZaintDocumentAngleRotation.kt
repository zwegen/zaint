package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Rotates all layers around the document centre without changing its dimensions. */
class ZaintDocumentAngleRotation(private val angle: Float) : Command {
    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        layerModel.layers.forEach { layer ->
            layer.bitmap = LayerRotateCommand.rotateBitmap(layer.bitmap, angle)
        }
    }

    override fun freeResources() = Unit
}
