package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R

/** Displays one of the editor errors that needs no follow-up action. */
class ZaintErrorNoticeDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val error = requireArguments()
            .getString(ARGUMENT_ERROR)
            ?.let(EditorError::valueOf)
            ?: error("Missing editor error")

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setIcon(R.drawable.ic_zpaint_dialog_warning)
            .setTitle(error.titleResource)
            .setMessage(error.messageResource)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    enum class EditorError(
        @get:StringRes val titleResource: Int,
        @get:StringRes val messageResource: Int
    ) {
        SAVE_FAILURE(R.string.dialog_error_save_title, R.string.dialog_error_sdcard_text),
        LOAD_FAILURE(R.string.dialog_loading_image_failed_text, R.string.dialog_loading_image_failed_title)
    }

    companion object {
        private const val ARGUMENT_ERROR = "zaint.editor.error"

        fun forError(error: EditorError): ZaintErrorNoticeDialog =
            ZaintErrorNoticeDialog().apply {
                arguments = bundleOf(ARGUMENT_ERROR to error.name)
            }
    }
}
