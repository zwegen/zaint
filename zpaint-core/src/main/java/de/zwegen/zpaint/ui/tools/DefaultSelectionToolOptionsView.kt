package de.zwegen.zpaint.ui.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import de.zwegen.zpaint.R
import de.zwegen.zpaint.motif.MotifSelectionTarget
import de.zwegen.zpaint.tools.options.ZaintClipboardOptions
import de.zwegen.zpaint.tools.options.SelectionShape
import de.zwegen.zpaint.tools.options.SelectionToolOptionsView

class DefaultSelectionToolOptionsView(rootView: ViewGroup) : SelectionToolOptionsView {
    private val rectangleButton: MaterialButton
    private val circleButton: MaterialButton
    private val freeButton: MaterialButton
    private val motifButton: MaterialButton
    private val deleteButton: MaterialButton
    private val copyButton: MaterialButton
    private val cutButton: MaterialButton
    private val selectionToolOptionsView: View

    private var callback: ZaintClipboardOptions.Callback? = null
    private var shapeChangedListener: ((SelectionShape) -> Unit)? = null

    private fun updateShapeControls(motif: Boolean) {
        copyButton.visibility = if (motif) View.GONE else View.VISIBLE
        cutButton.visibility = copyButton.visibility
        if (!motif) {
            motifButton.setIconResource(R.drawable.ic_zpaint_selection_motif)
            motifButton.contentDescription = motifButton.context.getString(R.string.selection_tool_shape_motif)
        }
        val deleteLabel = if (motif) R.string.selection_motif_cancel else R.string.selection_tool_delete
        deleteButton.contentDescription = deleteButton.context.getString(deleteLabel)
    }

    private fun initializeListeners() {
        fun select(shape: SelectionShape) {
            updateShapeControls(shape == SelectionShape.MOTIF)
            shapeChangedListener?.invoke(shape)
        }
        rectangleButton.setOnClickListener { select(SelectionShape.RECTANGLE) }
        circleButton.setOnClickListener { select(SelectionShape.CIRCLE) }
        freeButton.setOnClickListener { select(SelectionShape.FREE) }
        motifButton.setOnClickListener { select(SelectionShape.MOTIF) }

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

    override fun showCutoutConfirmation() {
        selectionToolOptionsView.post {
            if (selectionToolOptionsView.isAttachedToWindow) {
                Snackbar.make(selectionToolOptionsView, R.string.selection_cutout_done, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun setMotifTarget(target: MotifSelectionTarget) {
        motifButton.setIconResource(
            if (target == MotifSelectionTarget.BACKGROUND) R.drawable.ic_zpaint_selection_background
            else R.drawable.ic_zpaint_selection_motif
        )
        motifButton.contentDescription = motifButton.context.getString(target.label)
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
        motifButton = selectionToolOptionsRoot.findViewById(R.id.action_shape_motif)
        copyButton = selectionToolOptionsRoot.findViewById(R.id.action_copy)
        deleteButton = selectionToolOptionsRoot.findViewById(R.id.action_delete)
        cutButton = selectionToolOptionsRoot.findViewById(R.id.action_cut)
        updateShapeControls(motifButton.isChecked)
        enablePaste(false)
        initializeListeners()
        selectionToolOptionsView = selectionToolOptionsRoot.findViewById(R.id.zpaint_layout_selection_tool_options)
    }
}
