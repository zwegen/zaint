package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatButton
import androidx.core.os.bundleOf
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.R
import de.zwegen.zpaint.common.SAVE_INFORMATION_DIALOG_TAG
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView

/** Confirms replacing a file in the folder currently selected by the Zaint user. */
class ZaintOverwriteConfirmationDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        val permissionCode = arguments.getInt(ARGUMENT_PERMISSION_CODE)
        val isExport = arguments.getBoolean(ARGUMENT_IS_EXPORT)

        val content = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_zaint_overwrite_confirmation,
            null
        )
        content.findViewById<TextView>(R.id.zaint_overwrite_title)
            .setText(R.string.zpaint_overwrite_title)
        content.findViewById<TextView>(R.id.zaint_overwrite_message).text =
            getString(R.string.zpaint_overwrite, getString(R.string.menu_save_copy))
        content.findViewById<AppCompatButton>(R.id.zaint_overwrite_cancel).apply {
            setText(R.string.cancel_button_text)
            setOnClickListener { dismiss() }
        }
        content.findViewById<AppCompatButton>(R.id.zaint_overwrite_confirm).apply {
            setText(R.string.overwrite_button_text)
            setOnClickListener {
                overwriteInCurrentFolder(permissionCode, isExport)
                (parentFragmentManager.findFragmentByTag(SAVE_INFORMATION_DIALOG_TAG) as? AppCompatDialogFragment)
                    ?.dismiss()
                dismiss()
            }
        }

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setView(content)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun overwriteInCurrentFolder(permissionCode: Int, isExport: Boolean) {
        val imageUri = ZaintDocumentStorage.storeImageUri
            ?: ZaintDocumentStorage.getUriForFilenameInCurrentSaveFolder(
                ZaintDocumentStorage.defaultFileName,
                requireContext().contentResolver
            )
        ZaintDocumentStorage.storeImageUri = imageUri
        (requireActivity() as MainView).presenter.switchBetweenVersions(permissionCode, isExport)
    }

    companion object {
        private const val ARGUMENT_PERMISSION_CODE = "zaint.overwrite.permission_code"
        private const val ARGUMENT_IS_EXPORT = "zaint.overwrite.is_export"

        fun forSaveTarget(
            permissionCode: Int,
            isExport: Boolean
        ): ZaintOverwriteConfirmationDialog = ZaintOverwriteConfirmationDialog().apply {
            arguments = bundleOf(
                ARGUMENT_PERMISSION_CODE to permissionCode,
                ARGUMENT_IS_EXPORT to isExport
            )
        }
    }
}
