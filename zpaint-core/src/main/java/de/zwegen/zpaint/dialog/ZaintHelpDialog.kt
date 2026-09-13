package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R

/** Shared help window for the current Zaint tool or filter. */
class ZaintHelpDialog : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_zpaint_help, null)
        content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_title).setText(
            requireArguments().getInt(ARG_TITLE)
        )
        content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_text).setText(
            requireArguments().getInt(ARG_TEXT)
        )
        if (requireArguments().getBoolean(ARG_SHOW_VERSION)) {
            content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_version).apply {
                text = getString(
                    R.string.info_version_format,
                    requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
                )
            }
            content.findViewById<View>(R.id.zpaint_help_dialog_footer).visibility = View.VISIBLE
        }
        content.findViewById<View>(R.id.zpaint_help_close_button).setOnClickListener { dismiss() }

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setView(content)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    companion object {
        const val TAG = "zaint_help"
        private const val ARG_TITLE = "title"
        private const val ARG_TEXT = "text"
        private const val ARG_SHOW_VERSION = "show_version"

        fun newInstance(@StringRes title: Int, @StringRes text: Int, showVersion: Boolean = false) = ZaintHelpDialog().apply {
            arguments = bundleOf(ARG_TITLE to title, ARG_TEXT to text, ARG_SHOW_VERSION to showVersion)
        }
    }
}
