package de.zwegen.zpaint.ui.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.chip.Chip
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.options.ZaintClipboardOptions

/**
 * Connects Zaint's existing copy, cut and paste controls to the clipboard
 * tool. State changes stay in the tool; this adapter only renders them.
 */
class DefaultZaintClipboardOptions(root: ViewGroup) : ZaintClipboardOptions {
    private val copyAction: Chip
    private val cutAction: Chip
    private val pasteAction: Chip
    private val optionsLayout: View
    private var callback: ZaintClipboardOptions.Callback? = null

    init {
        val content = LayoutInflater.from(root.context).inflate(
            R.layout.dialog_zpaint_clipboard_tool,
            root
        )
        copyAction = content.findViewById(R.id.action_copy)
        cutAction = content.findViewById(R.id.action_cut)
        pasteAction = content.findViewById(R.id.action_paste)
        optionsLayout = content.findViewById(R.id.zpaint_layout_clipboard_tool_options)

        pasteAction.isEnabled = false
        bindActions()
    }

    override fun setCallback(callback: ZaintClipboardOptions.Callback) {
        this.callback = callback
    }

    override fun enablePaste(enable: Boolean) {
        pasteAction.isEnabled = enable
    }

    override fun toggleShapeSizeVisibility(isVisible: Boolean) = Unit

    override fun getClipboardToolOptionsLayout(): View = optionsLayout

    override fun setShapeSizeText(shapeSize: String) = Unit

    private fun bindActions() {
        copyAction.setOnClickListener { callback?.copyClicked() }
        cutAction.setOnClickListener { callback?.cutClicked() }
        pasteAction.setOnClickListener { callback?.pasteClicked() }
    }
}
