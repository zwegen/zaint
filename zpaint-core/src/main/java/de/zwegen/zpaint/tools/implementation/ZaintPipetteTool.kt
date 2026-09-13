package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController

class ZaintPipetteTool(
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    private val listener: OnColorPickedListener,
    private val colorChangeTarget: ColorChangeTarget,
    private val addToColorHistory: (Int) -> Unit
) : ZaintToolBase(contextCallback, toolOptionsViewController, toolPaint, workspace, idlingResource, commandManager) {
    private var surfaceBitmap: Bitmap? = null
    private var onColorPickedForActiveTool: ((Int) -> Unit)? = null

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.PIPETTE

    override var drawTime: Long = 0
    override fun handleUpAnimations(coordinate: PointF?) {
      super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    init {
        updateSurfaceBitmap()
    }

    override fun draw(canvas: Canvas) = Unit

    override fun handleDown(coordinate: PointF?): Boolean = setColor(coordinate)

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean = setColor(coordinate)

    override fun handleUp(coordinate: PointF?): Boolean = setColor(coordinate, true)

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun resetInternalState() {
        updateSurfaceBitmap()
    }

    /** Enables a one-shot pick that returns to the tool which opened the color picker. */
    fun pickColorForActiveTool(listener: (Int) -> Unit) {
        onColorPickedForActiveTool = listener
        updateSurfaceBitmap()
    }

    private fun setColor(coordinate: PointF?, saveCommand: Boolean = false): Boolean {
        if (coordinate == null || !workspace.contains(coordinate)) {
            return false
        }
        val color =
            surfaceBitmap?.getPixel(coordinate.x.toInt(), coordinate.y.toInt()) ?: return false
        listener.colorChanged(color)
        ZaintBrushTool.updateSharedActivePresetColor(color)
        changePaintColor(color)
        if (saveCommand) {
            addToColorHistory(color)
            onColorPickedForActiveTool?.let { listener ->
                onColorPickedForActiveTool = null
                listener(color)
            } ?: run {
                val command = commandFactory.createColorChangedCommand(this, colorChangeTarget, color)
                commandManager.addCommandWithoutUndo(command)
            }
        }
        return true
    }

    private fun updateSurfaceBitmap() {
        surfaceBitmap = workspace.bitmapOfAllLayers
    }
}
