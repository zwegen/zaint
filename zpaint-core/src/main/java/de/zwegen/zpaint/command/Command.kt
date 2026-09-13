package de.zwegen.zpaint.command

import android.graphics.Canvas
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** A reversible drawing operation applied to one canvas and its layer model. */
interface Command {
    fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model)
    fun freeResources()
}
