package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatButton
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView

/** Lets the editor save or discard unsaved changes before changing the document. */
class ZaintUnsavedChangesDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val destination = requireArguments()
            .getString(ARGUMENT_DESTINATION)
            ?.let(UnsavedChangesDestination::valueOf)
            ?: error("Missing unsaved changes destination")

        val content = LayoutInflater.from(requireContext()).inflate(
            R.layout.dialog_zaint_unsaved_changes,
            null
        )
        content.findViewById<TextView>(R.id.zaint_unsaved_changes_title).setText(destination.titleResource)
        content.findViewById<TextView>(R.id.zaint_unsaved_changes_message).setText(destination.messageResource)
        content.findViewById<AppCompatButton>(R.id.zaint_unsaved_changes_discard).apply {
            setText(R.string.discard_button_text)
            setOnClickListener {
                discard(destination)
                dismiss()
            }
        }
        content.findViewById<AppCompatButton>(R.id.zaint_unsaved_changes_save).apply {
            setText(R.string.save_button_text)
            setOnClickListener {
                saveBefore(destination)
                dismiss()
            }
        }

        return AlertDialog.Builder(requireActivity(), R.style.ZPaintAlertDialog)
            .setView(content)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun saveBefore(destination: UnsavedChangesDestination) {
        when (destination) {
            UnsavedChangesDestination.EXIT -> hostPresenter().saveBeforeFinish()
            UnsavedChangesDestination.LOAD_IMAGE -> hostPresenter().saveBeforeLoadImage()
            UnsavedChangesDestination.NEW_IMAGE -> hostPresenter().saveBeforeNewImage()
        }
    }

    private fun discard(destination: UnsavedChangesDestination) {
        when (destination) {
            UnsavedChangesDestination.EXIT -> hostPresenter().finishActivity()
            UnsavedChangesDestination.LOAD_IMAGE -> hostPresenter().loadNewImage()
            UnsavedChangesDestination.NEW_IMAGE -> hostPresenter().onNewImage()
        }
    }

    private fun hostPresenter() = (requireActivity() as MainView).presenter

    enum class UnsavedChangesDestination(
        @get:StringRes val titleResource: Int,
        @get:StringRes val messageResource: Int
    ) {
        EXIT(R.string.closing_security_question_title, R.string.closing_security_question),
        LOAD_IMAGE(R.string.menu_load_image, R.string.dialog_warning_new_image),
        NEW_IMAGE(R.string.menu_new_image, R.string.dialog_warning_new_image)
    }

    companion object {
        private const val ARGUMENT_DESTINATION = "zaint.unsaved.destination"

        fun forDestination(destination: UnsavedChangesDestination): ZaintUnsavedChangesDialog =
            ZaintUnsavedChangesDialog().apply {
                arguments = bundleOf(ARGUMENT_DESTINATION to destination.name)
            }
    }
}
