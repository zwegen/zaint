package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatButton
import de.zwegen.zpaint.R
import de.zwegen.zpaint.ZaintEditorActivity

/** Confirms the flattening of the currently active layers. */
class ZaintMergeActiveLayersDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_zaint_merge_active_layers,
            null
        )
        content.findViewById<TextView>(R.id.zaint_merge_layers_title)
            .setText(R.string.layer_merge_confirmation_title)
        content.findViewById<TextView>(R.id.zaint_merge_layers_message)
            .setText(R.string.layer_merge_confirmation_message)
        content.findViewById<AppCompatButton>(R.id.zaint_merge_layers_cancel).apply {
            setText(R.string.cancel_button_text)
            setOnClickListener { dismiss() }
        }
        content.findViewById<AppCompatButton>(R.id.zaint_merge_layers_confirm).apply {
            setText(R.string.layer_merge_confirm_button)
            setOnClickListener {
                (requireActivity() as ZaintEditorActivity).mergeActiveLayers()
                dismiss()
            }
        }

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setView(content)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    companion object {
        const val TAG = "zaint.merge.active.layers"
    }
}
