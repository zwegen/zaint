package de.zwegen.zpaint.ui.viewholder

import androidx.drawerlayout.widget.DrawerLayout
import de.zwegen.zpaint.contract.ZaintEditorContracts

/** Adapts the Android drawer widget to the presenter-facing drawer contract. */
class DrawerLayoutViewHolder(private val host: DrawerLayout) : ZaintEditorContracts.DrawerLayoutViewHolder {
    override fun closeDrawer(gravity: Int, animate: Boolean) {
        host.closeDrawer(gravity, animate)
    }

    override fun isDrawerOpen(gravity: Int): Boolean {
        return host.isDrawerOpen(gravity)
    }

    override fun isDrawerVisible(gravity: Int): Boolean {
        return host.isDrawerVisible(gravity)
    }

    override fun openDrawer(gravity: Int) {
        host.openDrawer(gravity)
    }
}
