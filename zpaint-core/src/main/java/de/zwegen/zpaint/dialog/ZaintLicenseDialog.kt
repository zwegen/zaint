package de.zwegen.zpaint.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import de.zwegen.zpaint.R

/** Offline index and reader for the license documents shipped with the editor. */
class ZaintLicenseDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_zaint_licenses, null)
        content.findViewById<TextView>(R.id.zaint_license_title).text = requireArguments().getString(ARG_TITLE)
        content.findViewById<View>(R.id.zaint_license_close).setOnClickListener { dismiss() }
        val list = content.findViewById<ListView>(android.R.id.list)
        val path = requireArguments().getString(ARG_PATH)
        if (path == null) {
            val entries = licenseEntries()
            list.adapter = ArrayAdapter(list.context, R.layout.zaint_license_entry, entries.keys.toList())
            list.setOnItemClickListener { _, _, position, _ ->
                val title = entries.keys.elementAt(position)
                newInstance(title, entries.getValue(title)).show(childFragmentManager, TAG)
            }
        } else {
            // Native-toolchain notices can exceed 800 KB. Lay out only the visible blocks.
            val blocks = requireContext().assets.open(path).bufferedReader().useLines { lines ->
                lines.chunked(24) { it.joinToString("\n") }.toList()
            }
            list.divider = null
            list.adapter = ArrayAdapter(list.context, R.layout.zaint_license_text_block, blocks)
        }
        return AlertDialog.Builder(requireContext(), R.style.ZPaintAlertDialog).setView(content).create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun licenseEntries(): Map<String, String> {
        val entries = linkedMapOf(
            "Zaint · GPL-3.0" to "licenses/Zaint-GPL-3.0.txt",
            "MagicTouch · NOTICE" to "motif/notices/NOTICE.txt",
            "MagicTouch · Apache-2.0" to "motif/notices/MagicTouch-Apache-2.0.txt",
            "LiteRT · TensorFlow" to "litert-notices/TensorFlow-LICENSE.txt",
            "LiteRT · C library" to "litert-notices/C-library-LICENSE.txt"
        )
        val assets = requireContext().assets
        val root = "motif/notices/third-party"
        assets.open("licenses/third-party-index.txt").bufferedReader().useLines { paths ->
            paths.filter { it.isNotBlank() }.forEach { path ->
                entries[path.replace("/", " · ")] = "$root/$path"
            }
        }
        return entries
    }

    companion object {
        const val TAG = "zaint_licenses"
        private const val ARG_TITLE = "title"
        private const val ARG_PATH = "path"

        fun newInstance(title: String, path: String? = null) = ZaintLicenseDialog().apply {
            arguments = bundleOf(ARG_TITLE to title, ARG_PATH to path)
        }
    }
}
