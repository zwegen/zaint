package de.zwegen.zpaint.ui

import com.google.android.material.bottomnavigation.BottomNavigationView
import de.zwegen.zpaint.R
import de.zwegen.zpaint.contract.ZaintEditorContracts.BottomNavigationAppearance
import de.zwegen.zpaint.tools.ZaintToolKind

/** Displays the selected drawing tool in the portrait bottom navigation. */
class BottomNavigationPortrait(private val navigation: BottomNavigationView) : BottomNavigationAppearance {
    override fun showCurrentTool(toolType: ZaintToolKind) {
        val currentToolItem = navigation.menu.findItem(R.id.action_current_tool)
        currentToolItem.setIcon(toolType.drawableResource)
        currentToolItem.setTitle(toolType.nameResource)
    }
}
