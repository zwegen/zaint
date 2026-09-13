package de.zwegen.zpaint.command

import android.graphics.Canvas
import de.zwegen.zpaint.contract.ZaintLayerContracts

/**
 * Base command for drawing directly onto the canvas selected by the command manager.
 *
 * Drawing commands have no document-model mutation of their own and do not retain disposable
 * resources. This keeps the canvas boundary explicit for lightweight stroke commands.
 */
abstract class CanvasDrawingCommand : Command {
    final override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        drawOn(canvas)
    }

    final override fun freeResources() = Unit

    protected abstract fun drawOn(canvas: Canvas)
}
