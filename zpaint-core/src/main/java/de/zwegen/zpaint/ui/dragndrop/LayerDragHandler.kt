package de.zwegen.zpaint.ui.dragndrop

import android.view.View

/** Controls the lifetime of a layer-list drag gesture. */
interface LayerDragHandler {
    fun beginDrag(position: Int, view: View)

    fun endDrag()
}
