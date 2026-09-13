package de.zwegen.zpaint.tools.options

import de.zwegen.zpaint.command.implementation.FillGradientDirection

/**
 * The controls exposed by Zaint's fill tool.
 *
 * The tool owns fill state; this boundary only reports user choices and renders
 * the current palette.
 */
interface ZaintFillOptions {
    fun setListener(listener: Listener)
    fun renderFillColors(colors: List<Int>)
    fun renderImageFillSelected(selected: Boolean)

    interface Listener {
        fun onToleranceSelected(percent: Int)
        fun onGradientDirectionSelected(direction: FillGradientDirection)
        fun onAddFillColor()
        fun onFillColorSelected(index: Int)
        fun onRemoveFillColor(index: Int)
        fun onImageFillSelected()
        fun onImageFillDeselected()
    }
}
