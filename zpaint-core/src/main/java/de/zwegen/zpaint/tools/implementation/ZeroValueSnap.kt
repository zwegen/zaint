package de.zwegen.zpaint.tools.implementation

import kotlin.math.abs

/** Keeps a continuous value at zero until the drag deliberately leaves its release range. */
class ZeroValueSnap(
    private val snapDistance: Float,
    private val releaseDistance: Float
) {
    fun resolve(value: Float, locked: Boolean): Boolean =
        if (locked) abs(value) <= releaseDistance else abs(value) <= snapDistance
}
