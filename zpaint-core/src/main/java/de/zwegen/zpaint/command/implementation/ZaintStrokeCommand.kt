package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import android.graphics.Paint
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Immutable drawing command for one completed brush stroke. */
class ZaintStrokeCommand(paint: Paint, path: ZaintStrokePath) : Command {
    private val strokePaint = Paint(paint)
    private val strokePath = ZaintStrokePath(path)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        canvas.drawPath(strokePath, strokePaint)
    }

    override fun freeResources() = Unit
}
