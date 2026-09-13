package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R
import de.zwegen.zpaint.common.ZaintActivityCodes.LoadImageRequestCode
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView

/** Confirms whether the selected image should be scaled while it is opened. */
class ZaintImageScaleDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        val imageUri = arguments.getString(ARGUMENT_IMAGE_URI)
            ?.let(Uri::parse)
            ?: error("Missing image URI")
        val requestCode = arguments.getInt(ARGUMENT_REQUEST_CODE)

        return AlertDialog.Builder(requireActivity(), R.style.ZPaintAlertDialog)
            .setTitle(R.string.dialog_scale_title)
            .setMessage(R.string.dialog_scale_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (requireActivity() as MainView).presenter.loadScaledImage(imageUri, requestCode)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        private const val ARGUMENT_IMAGE_URI = "zaint.scale.image_uri"
        private const val ARGUMENT_REQUEST_CODE = "zaint.scale.request_code"

        fun forImage(
            imageUri: Uri,
            @LoadImageRequestCode requestCode: Int
        ): ZaintImageScaleDialog = ZaintImageScaleDialog().apply {
            arguments = bundleOf(
                ARGUMENT_IMAGE_URI to imageUri.toString(),
                ARGUMENT_REQUEST_CODE to requestCode
            )
        }
    }
}
