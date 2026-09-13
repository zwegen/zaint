package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import de.zwegen.zpaint.R

/** A short-lived Zaint progress dialog that cannot be cancelled by touch. */
class IndeterminateProgressDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        return AlertDialog.Builder(requireContext(), R.style.ZPaintProgressDialog)
            .setView(R.layout.zpaint_layout_indeterminate)
            .create()
    }

    override fun onPause() {
        super.onPause()
        dismissAllowingStateLoss()
    }
}
