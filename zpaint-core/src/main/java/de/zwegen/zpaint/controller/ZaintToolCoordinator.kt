package de.zwegen.zpaint.controller

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolFactory
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintClipboardTool
import de.zwegen.zpaint.tools.implementation.IconTool
import de.zwegen.zpaint.tools.implementation.ZaintImportTool
import de.zwegen.zpaint.tools.implementation.ZaintFillTool
import de.zwegen.zpaint.tools.implementation.ZaintLineTool
import de.zwegen.zpaint.tools.implementation.ZaintPipetteTool
import de.zwegen.zpaint.tools.implementation.ZaintTextTool
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController

/**
 * Coordinates Zaint's active tool, its option panel, and state hand-off when a
 * tool is recreated. Tool rendering, drawing and individual options remain in
 * their respective tool classes.
 */
class ZaintToolCoordinator(
    private val activeTool: ToolReference,
    private val options: ZaintToolOptionsController,
    private val factory: ToolFactory,
    private val commands: ZaintCommandTimeline,
    private val workspace: Workspace,
    private val idlingResource: CountingIdlingResource,
    private val paint: ToolPaint,
    private val androidContext: ContextCallback
) : ZaintToolControl {
    private lateinit var colorListener: OnColorPickedListener

    override val isDefaultTool: Boolean
        get() = toolType == ZaintToolKind.BRUSH

    override val toolColor: Int?
        get() = currentTool?.drawPaint?.color

    override val currentTool: Tool?
        get() = activeTool.tool

    override val toolType: ZaintToolKind?
        get() = currentTool?.toolType

    override fun setOnColorPickedListener(listener: OnColorPickedListener) {
        colorListener = listener
    }

    /**
     * Runs one pick with the normal Pipette tool while keeping the current tool and its
     * option state intact. The sampled color is then handed back to that original tool.
     */
    override fun selectPipetteColor() {
        val previousTool = currentTool ?: return
        if (previousTool is ZaintPipetteTool) return

        options.hide()
        val pipette = factory.createTool(
            ZaintToolKind.PIPETTE,
            options,
            commands,
            workspace,
            idlingResource,
            paint,
            androidContext,
            colorListener
        ) as ZaintPipetteTool
        pipette.pickColorForActiveTool { color ->
            previousTool.changePaintColor(color)
            activeTool.tool = previousTool
            if (previousTool.toolType.hasOptions()) options.show() else options.hide()
            workspace.invalidate()
        }
        activeTool.tool = pipette
        workspace.invalidate()
    }

    override fun switchTool(toolType: ZaintToolKind) {
        val replacement = buildTool(toolType)
        replaceActiveTool(replacement)
    }

    private fun buildTool(toolType: ZaintToolKind): Tool {
        prepareOptionsFor(toolType)
        return factory.createTool(
            toolType,
            options,
            commands,
            workspace,
            idlingResource,
            paint,
            androidContext,
            colorListener
        ).also { presentOptionsFor(toolType) }
    }

    private fun prepareOptionsFor(toolType: ZaintToolKind) {
        if (toolType != ZaintToolKind.HAND) {
            options.removeToolViews()
        }
        if (ToolInteractionPolicy.requiresExplicitApply(toolType)) {
            options.showCheckmark()
        } else {
            options.hideCheckmark()
        }
        if (ToolInteractionPolicy.createsLayer(toolType)) {
            options.showLayerPlus()
        } else {
            options.hideLayerPlus()
        }
    }

    private fun presentOptionsFor(toolType: ZaintToolKind) {
        if (toolType == ZaintToolKind.HAND || !toolType.hasOptions()) {
            options.hide()
            return
        }
        options.resetToOrigin()
        options.show()
    }

    private fun replaceActiveTool(replacement: Tool) {
        val previous = currentTool
        hideLinePlusButton(previous?.toolType)
        if (previous?.toolType == replacement.toolType) {
            val state = Bundle()
            previous.onSaveInstanceState(state)
            replacement.onRestoreInstanceState(state)
        }
        activeTool.tool = replacement
        workspace.invalidate()
    }

    private fun hideLinePlusButton(previousType: ZaintToolKind?) {
        if (previousType == ZaintToolKind.LINE && ZaintLineTool.topBarViewHolder?.plusButton?.visibility == View.VISIBLE) {
            ZaintLineTool.topBarViewHolder?.plusButton?.visibility = View.GONE
        }
    }

    override fun hideToolOptionsView() {
        changeCanvasToolLayout(hidden = true, updateOptionsPanel = true)
    }

    override fun hideToolOptionsViewImmediately() {
        changeCanvasToolLayout(hidden = true, updateOptionsPanel = false)
        options.hideImmediately()
    }

    override fun hideToolOptionsViewForCanvasTools() {
        changeCanvasToolLayout(hidden = true, updateOptionsPanel = false)
    }

    override fun showToolOptionsView() {
        options.show()
        changeCanvasToolLayout(hidden = false, updateOptionsPanel = false)
    }

    private fun changeCanvasToolLayout(hidden: Boolean, updateOptionsPanel: Boolean) {
        when (val tool = currentTool) {
            is ZaintTextTool -> if (hidden) tool.hideTextToolLayout() else tool.showTextToolLayout()
            is IconTool -> tool.changeIconToolLayoutVisibility(hidden, true)
            is ZaintClipboardTool -> tool.changeClipboardToolLayoutVisibility(hidden, true)
            else -> if (updateOptionsPanel) options.hide()
        }
    }

    override fun toolOptionsViewVisible(): Boolean = options.isVisible

    override fun resetToolInternalState() {
        currentTool?.resetInternalState(Tool.StateChange.RESET_INTERNAL_STATE)
    }

    override fun resetToolInternalStateOnImageLoaded() {
        currentTool?.resetInternalState(Tool.StateChange.NEW_IMAGE_LOADED)
    }

    override fun disableToolOptionsView() {
        if (toolType != ZaintToolKind.IMPORTPNG) options.disable()
    }

    override fun disableHideOption() {
        if (toolType != ZaintToolKind.IMPORTPNG) options.disableHide()
    }

    override fun enableHideOption() {
        options.enableHide()
    }

    override fun enableToolOptionsView() {
        options.enable()
    }

    override fun createTool() {
        val previous = currentTool
        if (previous == null) {
            activeTool.tool = buildTool(ZaintToolKind.BRUSH)
        } else {
            val state = Bundle()
            previous.onSaveInstanceState(state)
            activeTool.tool = buildTool(previous.toolType)
            activeTool.tool?.onRestoreInstanceState(state)
        }
        workspace.invalidate()
    }

    override fun toggleToolOptionsView() {
        if (options.isVisible) {
            options.hide()
        } else {
            showToolOptionsView()
        }
    }

    override fun hasToolOptionsView(): Boolean = toolType?.hasOptions() ?: false

    override fun setBitmapFromSource(bitmap: Bitmap?) {
        if (bitmap == null) return
        when (val tool = currentTool) {
            is ZaintImportTool -> tool.setBitmapFromSource(bitmap)
            is ZaintFillTool -> tool.setBitmapFromSource(bitmap)
        }
    }
}
