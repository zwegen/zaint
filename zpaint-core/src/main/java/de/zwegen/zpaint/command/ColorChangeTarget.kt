package de.zwegen.zpaint.command

/**
 * Small application boundary used by color commands.
 *
 * Commands only need a way to reach the UI thread and refresh the current
 * color indicator. They must not depend on an Activity implementation.
 */
interface ColorChangeTarget {
    fun runColorChange(block: () -> Unit)

    fun updateColorIndicator(color: Int)
}
