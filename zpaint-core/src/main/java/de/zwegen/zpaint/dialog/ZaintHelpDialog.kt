package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.text.Annotation
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R

/** Shared help window for the current Zaint tool or filter. */
class ZaintHelpDialog : AppCompatDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_zpaint_help, null)
        content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_title).setText(
            requireArguments().getInt(ARG_TITLE)
        )
        val body = getText(requireArguments().getInt(ARG_TEXT))
        val hint = requireArguments().getInt(ARG_HINT)
        val bodyView = content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_text)
        bodyView.text = if (hint == 0) body else TextUtils.concat(body, "\n\n", getText(hint))
        if (requireArguments().getBoolean(ARG_SHOW_VERSION)) {
            val linkColor = ContextCompat.getColor(requireContext(), R.color.zpaint_text_color_link)
            bodyView.setLinkTextColor(linkColor)
            bodyView.text = withLicenseLink(bodyView.text)
            bodyView.movementMethod = LinkMovementMethod.getInstance()
            content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_github).apply {
                setTextColor(linkColor)
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) }
            }
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

    private fun withLicenseLink(text: CharSequence): SpannableString {
        val linkedText = SpannableString(text)
        for (annotation in linkedText.getSpans(0, text.length, Annotation::class.java)) {
            if (annotation.key != "link" || annotation.value != "licenses") continue
            val start = linkedText.getSpanStart(annotation)
            val end = linkedText.getSpanEnd(annotation)
            val title = text.subSequence(start, end).toString()
            linkedText.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    ZaintLicenseDialog.newInstance(title).show(childFragmentManager, ZaintLicenseDialog.TAG)
                }
            }, start, end, linkedText.getSpanFlags(annotation))
        }
        return linkedText
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    companion object {
        const val TAG = "zaint_help"
        private const val ARG_TITLE = "title"
        private const val ARG_TEXT = "text"
        private const val ARG_HINT = "hint"
        private const val ARG_SHOW_VERSION = "show_version"
        private const val GITHUB_URL = "https://github.com/zwegen/zaint"

        fun newInstance(
            @StringRes title: Int, @StringRes text: Int, showVersion: Boolean = false, @StringRes hint: Int = 0
        ) = ZaintHelpDialog().apply {
            arguments = bundleOf(ARG_TITLE to title, ARG_TEXT to text, ARG_SHOW_VERSION to showVersion, ARG_HINT to hint)
        }
    }
}
