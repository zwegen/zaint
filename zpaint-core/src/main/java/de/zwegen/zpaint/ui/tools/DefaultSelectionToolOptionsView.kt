package de.zwegen.zpaint.ui.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.options.ZaintClipboardOptions
import de.zwegen.zpaint.tools.options.SelectionShape
import de.zwegen.zpaint.tools.options.SelectionToolOptionsView

class DefaultSelectionToolOptionsView(rootView: ViewGroup) : SelectionToolOptionsView {
    private val rectangleButton: MaterialButton
    private val circleButton: MaterialButton
    private val freeButton: MaterialButton
    private val deleteButton: MaterialButton
    private val copyButton: MaterialButton
    private val cutButton: MaterialButton
    private val selectionToolOptionsView: View

    private var callback: ZaintClipboardOptions.Callback? = null
    private var shapeChangedListener: ((SelectionShape) -> Unit)? = null

    private fun initializeListeners() {
        rectangleButton.setOnClickListener {
            shapeChangedListener?.invoke(SelectionShape.RECTANGLE)
        }

        circleButton.setOnClickListener {
            shapeChangedListener?.invoke(SelectionShape.CIRCLE)
        }

        freeButton.setOnClickListener {
            shapeChangedListener?.invoke(SelectionShape.FREE)
        }

        copyButton.setOnClickListener {
            callback?.copyClicked()
        }

        cutButton.setOnClickListener {
            callback?.cutClicked()
        }

        deleteButton.setOnClickListener {
            callback?.deleteClicked()
        }
    }

    override fun setCallback(callback: ZaintClipboardOptions.Callback) {
        this.callback = callback
    }

    override fun setShapeChangedListener(listener: (SelectionShape) -> Unit) {
        shapeChangedListener = listener
    }

    override fun enablePaste(enable: Boolean) {
        // Paste is confirmed with the top checkmark in the selection tool.
    }

    override fun toggleShapeSizeVisibility(isVisible: Boolean) = Unit

    override fun getClipboardToolOptionsLayout(): View = selectionToolOptionsView

    override fun setShapeSizeText(shapeSize: String) = Unit

    init {
        val inflater = LayoutInflater.from(rootView.context)
        val selectionToolOptionsRoot =
            inflater.inflate(R.layout.dialog_zpaint_selection_tool, rootView)
        rectangleButton = selectionToolOptionsRoot.findViewById(R.id.action_shape_rectangle)
        circleButton = selectionToolOptionsRoot.findViewById(R.id.action_shape_circle)
        freeButton = selectionToolOptionsRoot.findViewById(R.id.action_shape_free)
        copyButton = selectionToolOptionsRoot.findViewById(R.id.action_copy)
        deleteButton = selectionToolOptionsRoot.findViewById(R.id.action_delete)
        cutButton = selectionToolOptionsRoot.findViewById(R.id.action_cut)
        enablePaste(false)
        initializeListeners()
        selectionToolOptionsView = selectionToolOptionsRoot.findViewById(R.id.zpaint_layout_selection_tool_options)
    }
}
