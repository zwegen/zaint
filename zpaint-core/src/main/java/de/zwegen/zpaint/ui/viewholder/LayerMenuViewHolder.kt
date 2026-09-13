package de.zwegen.zpaint.ui.viewholder

import android.view.View
import com.google.android.material.navigation.NavigationView
import de.zwegen.zpaint.R
import de.zwegen.zpaint.contract.ZaintLayerContracts

/**
 * Zaint's adapter for enabling and disabling actions in the layer drawer.
 * The presenter decides which actions are allowed; this class only applies
 * that state to the existing UI.
 */
class LayerMenuViewHolder(
    private val navigationView: NavigationView
) : ZaintLayerContracts.LayerMenuViewHolder {
    val layerAddButton: View = navigationView.findViewById(R.id.zpaint_layer_side_nav_button_add)
    val layerDuplicateButton: View = navigationView.findViewById(R.id.zpaint_layer_side_nav_button_duplicate)
    val layerDeleteButton: View = navigationView.findViewById(R.id.zpaint_layer_side_nav_button_delete)
    val layerMergeButton: View = navigationView.findViewById(R.id.zpaint_layer_side_nav_button_merge)
    val layerVisibilityButton: View = navigationView.findViewById(R.id.zpaint_layer_side_nav_button_visibility)
    val layerOpacityButton: View = navigationView.findViewById(R.id.zpaint_layer_side_nav_button_opacity)

    override fun isShown(): Boolean = navigationView.isShown

    override fun disableAddLayerButton() = setAddEnabled(false)

    override fun enableAddLayerButton() = setAddEnabled(true)

    override fun disableRemoveLayerButton() = setRemoveEnabled(false)

    override fun enableRemoveLayerButton() = setRemoveEnabled(true)

    override fun disableDuplicateLayerButton() = setDuplicateEnabled(false)

    override fun enableDuplicateLayerButton() = setDuplicateEnabled(true)

    override fun disableMergeLayerButton() = setMergeEnabled(false)

    override fun enableMergeLayerButton() = setMergeEnabled(true)

    override fun disableLayerVisibilityButton() = setVisibilityEnabled(false)

    override fun disableLayerOpacityButton() = setOpacityEnabled(false)

    private fun setAddEnabled(enabled: Boolean) {
        layerAddButton.isEnabled = enabled
    }

    private fun setRemoveEnabled(enabled: Boolean) {
        layerDeleteButton.isEnabled = enabled
    }

    private fun setDuplicateEnabled(enabled: Boolean) {
        layerDuplicateButton.isEnabled = enabled
    }

    private fun setMergeEnabled(enabled: Boolean) {
        layerMergeButton.isEnabled = enabled
    }

    private fun setVisibilityEnabled(enabled: Boolean) {
        layerVisibilityButton.isEnabled = enabled
    }

    private fun setOpacityEnabled(enabled: Boolean) {
        layerOpacityButton.isEnabled = enabled
    }
}
