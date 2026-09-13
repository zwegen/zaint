package de.zwegen.zpaint.tools.options

/** Coordinates the one visible tool-options panel and its transition callbacks. */
interface ToolOptionsVisibilityController {
    val isVisible: Boolean

    fun hide()

    fun setCallback(callback: Callback)

    fun show()

    fun showDelayed()

    interface Callback {
        fun onHide()

        fun onShow()
    }
}
