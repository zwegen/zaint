package de.zwegen.zpaint.ui.dragndrop

/** Keeps transient layer-drag state separate from the RecyclerView implementation. */
internal class LayerDragSession(val sourcePosition: Int) {
    var currentPosition: Int = sourcePosition
        private set

    var mergeTarget: Int? = null
        private set

    fun movePreviewTo(position: Int) {
        currentPosition = position
        mergeTarget = null
    }

    fun selectMergeTarget(position: Int) {
        mergeTarget = position
    }

    fun clearMergeTarget(): Boolean {
        val hadMergeTarget = mergeTarget != null
        mergeTarget = null
        return hadMergeTarget
    }

    fun finish(): Outcome = mergeTarget?.let { Outcome.Merge(sourcePosition, it) }
        ?: Outcome.Reorder(sourcePosition, currentPosition)

    sealed class Outcome {
        data class Merge(val sourcePosition: Int, val targetPosition: Int) : Outcome()
        data class Reorder(val sourcePosition: Int, val targetPosition: Int) : Outcome()
    }
}
