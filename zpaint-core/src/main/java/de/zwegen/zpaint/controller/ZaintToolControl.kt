package de.zwegen.zpaint.controller

import android.graphics.Bitmap
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ZaintToolKind

/**
 * Zaint's boundary between editor navigation and the currently active drawing tool.
 *
 * It owns tool creation and option-panel state. The individual tools retain their
 * own drawing and editing behavior.
 */
interface ZaintToolControl {
    val isDefaultTool: Boolean
    val toolType: ZaintToolKind?
    val toolColor: Int?
    val currentTool: Tool?
    val toolList: Set<ZaintToolKind>
        get() = ToolInteractionPolicy.explicitApplyTools

    fun setOnColorPickedListener(listener: OnColorPickedListener)
    fun selectPipetteColor()
    fun switchTool(toolType: ZaintToolKind)
    fun hideToolOptionsView()
    fun hideToolOptionsViewImmediately() = hideToolOptionsView()
    fun hideToolOptionsViewForCanvasTools()
    fun showToolOptionsView()
    fun toolOptionsViewVisible(): Boolean
    fun resetToolInternalState()
    fun resetToolInternalStateOnImageLoaded()
    fun disableToolOptionsView()
    fun disableHideOption()
    fun enableHideOption()
    fun enableToolOptionsView()
    fun createTool()
    fun toggleToolOptionsView()
    fun hasToolOptionsView(): Boolean
    fun setBitmapFromSource(bitmap: Bitmap?)
}
