package de.zwegen.zpaint.tools.options

import android.view.View
import android.view.ViewGroup

interface ZaintToolOptionsController : ToolOptionsVisibilityController {
    val toolSpecificOptionsLayout: ViewGroup
    val toolSpecificOptionsTopLayout: ViewGroup

    fun disable()

    fun enable()

    fun disableHide()

    fun enableHide()

    fun resetToOrigin()

    fun hideImmediately() = hide()

    fun removeToolViews()

    fun showCheckmark()

    fun showLayerCheckmark()

    fun hideCheckmark()

    fun showLayerPlus()

    fun hideLayerPlus()

    fun slideUp(view: View, willHide: Boolean, showOptionsView: Boolean, setViewGone: Boolean = false)

    fun slideDown(view: View, willHide: Boolean, showOptionsView: Boolean, setViewGone: Boolean = false)

}
