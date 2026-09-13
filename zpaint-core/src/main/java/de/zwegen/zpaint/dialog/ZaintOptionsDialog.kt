package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.LocaleListCompat
import de.zwegen.zpaint.R

/** App-wide options which are independent of the current image. */
class ZaintOptionsDialog : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_zaint_options, null)
        val englishSwitch = content.findViewById<SwitchCompat>(R.id.zaint_options_english_switch)
        englishSwitch.isChecked = AppCompatDelegate.getApplicationLocales().toLanguageTags() == ENGLISH_LANGUAGE_TAG
        content.findViewById<View>(R.id.zaint_options_english_info).setOnClickListener {
            ZaintHelpDialog.newInstance(R.string.option_english, R.string.option_english_info)
                .show(parentFragmentManager, ZaintHelpDialog.TAG)
        }
        englishSwitch.setOnCheckedChangeListener { _, useEnglish ->
            AppCompatDelegate.setApplicationLocales(
                if (useEnglish) LocaleListCompat.forLanguageTags(ENGLISH_LANGUAGE_TAG)
                else LocaleListCompat.getEmptyLocaleList()
            )
        }
        content.findViewById<View>(R.id.zaint_options_close_button).setOnClickListener { dismiss() }

        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog)
            .setView(content)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    companion object {
        const val TAG = "zaint_options"
        private const val ENGLISH_LANGUAGE_TAG = "en"
    }
}
