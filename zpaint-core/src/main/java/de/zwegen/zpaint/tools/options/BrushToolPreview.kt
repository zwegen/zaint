package de.zwegen.zpaint.tools.options

import de.zwegen.zpaint.tools.options.ZaintBrushOptions.PreviewState

/** Connects a brush-options view to the lightweight preview it renders. */
interface BrushToolPreview {
    fun setListener(callback: PreviewState)

    fun invalidate()
}
