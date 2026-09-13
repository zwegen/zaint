package de.zwegen.zpaint.tools.options

interface SelectionToolOptionsView : ZaintClipboardOptions {
    fun setShapeChangedListener(listener: (SelectionShape) -> Unit)
}

enum class SelectionShape {
    RECTANGLE,
    CIRCLE,
    FREE
}
