package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R

/** Explains why Zaint needs storage access before asking Android for it. */
class ZaintStoragePermissionDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        val permissions = arguments.getStringArray(ARGUMENT_PERMISSIONS)
            ?: error("Missing storage permissions")
        val requestCode = arguments.getInt(ARGUMENT_REQUEST_CODE)
        val request = arguments.getString(ARGUMENT_REQUEST)
            ?.let(StoragePermissionRequest::valueOf)
            ?: error("Missing storage permission request")

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setIcon(request.iconResource)
            .setMessage(request.messageResource)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ActivityCompat.requestPermissions(requireActivity(), permissions, requestCode)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    enum class StoragePermissionRequest(
        @get:DrawableRes val iconResource: Int,
        @get:StringRes val messageResource: Int
    ) {
        EXTERNAL_STORAGE(
            R.drawable.ic_zpaint_dialog_info,
            R.string.permission_info_external_storage_text
        )
    }

    companion object {
        private const val ARGUMENT_REQUEST = "zaint.storage.request"
        private const val ARGUMENT_PERMISSIONS = "zaint.storage.permissions"
        private const val ARGUMENT_REQUEST_CODE = "zaint.storage.request_code"

        fun forRequest(
            request: StoragePermissionRequest,
            permissions: Array<String>,
            requestCode: Int
        ): ZaintStoragePermissionDialog = ZaintStoragePermissionDialog().apply {
            arguments = bundleOf(
                ARGUMENT_REQUEST to request.name,
                ARGUMENT_PERMISSIONS to permissions,
                ARGUMENT_REQUEST_CODE to requestCode
            )
        }
    }
}
