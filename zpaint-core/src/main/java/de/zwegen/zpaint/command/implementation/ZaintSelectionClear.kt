package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import android.graphics.Point
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.cut.AndroidCanvasCutRenderer
import de.zwegen.zpaint.command.cut.CanvasCutRenderer
import de.zwegen.zpaint.command.cut.CutSelection
import de.zwegen.zpaint.command.cut.CutShape
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Clears one immutable Zaint selection from the active drawing canvas. */
class ZaintSelectionClear(
    center: Point,
    boxWidth: Float,
    boxHeight: Float,
    boxRotation: Float,
    shape: ZaintSelectionShape = ZaintSelectionShape.RECTANGLE,
    path: ZaintStrokePath? = null,
    private val cutRenderer: CanvasCutRenderer = AndroidCanvasCutRenderer()
) : Command {
    private val selection = CutSelection(
        center = Point(center.x, center.y),
        width = boxWidth,
        height = boxHeight,
        rotation = boxRotation,
        shape = shape.toRendererShape(),
        path = path?.let(::ZaintStrokePath)
    )

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        cutRenderer.clear(canvas, selection)
    }

    override fun freeResources() = Unit
}

/** Shapes supported by Zaint's destructive selection action. */
enum class ZaintSelectionShape {
    RECTANGLE,
    OVAL,
    PATH;

    fun toRendererShape(): CutShape = when (this) {
        RECTANGLE -> CutShape.RECTANGLE
        OVAL -> CutShape.OVAL
        PATH -> CutShape.PATH
    }
}
