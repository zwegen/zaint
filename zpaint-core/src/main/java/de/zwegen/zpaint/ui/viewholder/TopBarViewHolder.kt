package de.zwegen.zpaint.ui.viewholder

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import de.zwegen.zpaint.R
import de.zwegen.zpaint.contract.ZaintEditorContracts

/**
 * Zaint's direct view adapter for the controls above the drawing surface.
 * It only exposes the controls that the activity and presenter need; it
 * contains no tool or history decisions.
 */
class TopBarViewHolder(val layout: ViewGroup) : ZaintEditorContracts.TopBarViewHolder {
    val undoButton: ImageButton = layout.findViewById(R.id.zpaint_btn_top_undo)
    val helpButton: ImageButton = layout.findViewById(R.id.zpaint_btn_top_help)
    val redoButton: ImageButton = layout.findViewById(R.id.zpaint_btn_top_redo)
    val checkmarkButton: ImageButton = layout.findViewById(R.id.zpaint_btn_top_checkmark)
    val layerPlusButton: ImageButton = layout.findViewById(R.id.zpaint_btn_top_layer_plus)
    val checkmarkProgress: ProgressBar = layout.findViewById(R.id.zpaint_progress_top_checkmark)
    val menuButton: ImageButton = layout.findViewById(R.id.zpaint_btn_top_menu)
    var plusButton: ImageButton = layout.findViewById(R.id.zpaint_btn_top_plus)

    override val height: Int
        get() = layout.height

    override fun enableUndoButton() = setUndoEnabled(true)

    override fun disableUndoButton() = setUndoEnabled(false)

    override fun enableRedoButton() = setRedoEnabled(true)

    override fun disableRedoButton() = setRedoEnabled(false)

    override fun hide() = setBarVisible(false)

    override fun show() = setBarVisible(true)

    fun hidePlusButton() = setPlusVisible(false)

    fun showPlusButton() = setPlusVisible(true)

    private fun setUndoEnabled(enabled: Boolean) {
        undoButton.isEnabled = enabled
    }

    private fun setRedoEnabled(enabled: Boolean) {
        redoButton.isEnabled = enabled
    }

    private fun setBarVisible(visible: Boolean) {
        layout.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setPlusVisible(visible: Boolean) {
        plusButton.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
