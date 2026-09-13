package de.zwegen.zpaint.ui.dragndrop

/**
 * Boundary between the layer-list gesture and layer commands.
 *
 * Preview moves affect only the displayed order. The final move or merge is committed once the
 * user releases the dragged item.
 */
interface LayerDragCoordinator {
    fun previewMove(fromPosition: Int, targetPosition: Int): Int

    fun commitMerge(sourcePosition: Int, targetPosition: Int)

    fun commitReorder(sourcePosition: Int, targetPosition: Int)

    fun showMergeTarget(sourcePosition: Int, targetPosition: Int)
}
