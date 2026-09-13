package de.zwegen.zpaint.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.color.ColorChangeSequence
import de.zwegen.zpaint.command.color.PaintColorAction
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.implementation.ZaintLineTool
import de.zwegen.zpaint.tools.implementation.ZaintBrushTool

/** Applies one Zaint color choice through the active tool and its color-indicator boundary. */
class ZaintColorChange(
    private val tool: Tool?,
    private val target: ColorChangeTarget,
    private val color: Int
) : Command {
    private val sequence = ColorChangeSequence()

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        target.runColorChange { apply(tool, sequence.nextAction(tool is ZaintLineTool), undo = false) }
        if (tool !is ZaintBrushTool) target.runColorChange { target.updateColorIndicator(color) }
    }

    fun runInUndoMode() {
        target.runColorChange { apply(tool, sequence.nextAction(tool is ZaintLineTool), undo = true) }
    }

    private fun apply(activeTool: Tool?, action: PaintColorAction, undo: Boolean) = when (action) {
        PaintColorAction.RESTORE_LINE_COLOR -> if (undo) {
            (activeTool as ZaintLineTool).undoColorChangedCommand(color, false)
        } else {
            (activeTool as ZaintLineTool).undoColorChangedCommand(color)
        }
        PaintColorAction.SET_COLOR -> when (activeTool) {
            is ZaintBrushTool -> if (undo) activeTool.changePaintColor(color, false) else activeTool.changePaintColorFromColorPicker(color)
            else -> activeTool?.changePaintColor(color, !undo)
        }
    }

    override fun freeResources() = Unit
}
