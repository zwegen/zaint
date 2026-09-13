package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R

/** Sends the user to Zaint's Android settings after a permanent denial. */
class ZaintPermissionSettingsDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val packageName = requireArguments().getString(ARGUMENT_PACKAGE_NAME)
            ?: error("Missing application package name")

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setMessage(R.string.permission_info_permanent_denial_text)
            .setPositiveButton(R.string.dialog_settings) { _, _ ->
                val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
                startActivity(settingsIntent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        private const val ARGUMENT_PACKAGE_NAME = "zaint.application.package"

        fun forApplication(packageName: String): ZaintPermissionSettingsDialog =
            ZaintPermissionSettingsDialog().apply {
                arguments = bundleOf(ARGUMENT_PACKAGE_NAME to packageName)
            }
    }
}
