package de.zwegen.zpaint.tools.options

import de.zwegen.zpaint.motif.MotifSelectionTarget

interface SelectionToolOptionsView : ZaintClipboardOptions {
    fun showCutoutConfirmation()
    fun setMotifTarget(target: MotifSelectionTarget)
    fun setShapeChangedListener(listener: (SelectionShape) -> Unit)
}

enum class SelectionShape {
    RECTANGLE,
    CIRCLE,
    FREE,
    MOTIF
}
