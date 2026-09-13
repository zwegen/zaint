package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import de.zwegen.zpaint.R

/** Shared Zaint dialog for a short explanation of an image export format. */
abstract class ImageFormatInfoDialog : AppCompatDialogFragment() {
    @get:StringRes
    protected abstract val titleResource: Int

    @get:StringRes
    protected abstract val messageResource: Int

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setTitle(titleResource)
            .setMessage(messageResource)
            .setPositiveButton(R.string.zpaint_ok, null)
            .create()
}
