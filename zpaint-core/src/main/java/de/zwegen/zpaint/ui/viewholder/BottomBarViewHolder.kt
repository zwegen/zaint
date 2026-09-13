package de.zwegen.zpaint.ui.viewholder

import android.view.View
import androidx.annotation.IdRes
import de.zwegen.zpaint.contract.ZaintEditorContracts

/** Controls a bottom-area container without changing its contents. */
class BottomBarViewHolder(private val container: View) : ZaintEditorContracts.BottomBarViewHolder {
    override val isVisible: Boolean
        get() = container.visibility == View.VISIBLE

    override fun show() {
        updateVisibility(visible = true)
    }

    override fun hide() {
        updateVisibility(visible = false)
    }

    fun findButton(@IdRes id: Int): View? = container.findViewById(id)

    private fun updateVisibility(visible: Boolean) {
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
