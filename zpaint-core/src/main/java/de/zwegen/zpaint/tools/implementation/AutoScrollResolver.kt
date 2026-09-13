package de.zwegen.zpaint.tools.implementation

/** Calculates the canvas movement requested when a pointer approaches an edge. */
class AutoScrollResolver(private val tolerance: Int) {
    fun resolve(pointerX: Float, pointerY: Float, width: Int, height: Int): AutoScrollStep {
        val horizontal = when {
            pointerX < tolerance -> 1
            pointerX > width - tolerance -> -1
            else -> 0
        }
        val vertical = when {
            pointerY < tolerance -> 1
            pointerY > height - tolerance -> -1
            else -> 0
        }
        return AutoScrollStep(horizontal, vertical)
    }
}

data class AutoScrollStep(val horizontal: Int, val vertical: Int)
