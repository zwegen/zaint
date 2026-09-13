package de.zwegen.zpaint.tools.implementation

import kotlin.math.abs

/** Snaps a floating box to the four orthogonal angles with a stable release range. */
class FloatingBoxAngleSnap(
    private val snapDistanceDegrees: Float = 4f,
    private val releaseDistanceDegrees: Float = 8f
) {
    fun resolve(rotation: Float, lockedAngle: Float?): Float? {
        if (lockedAngle != null && angularDistance(rotation, lockedAngle) <= releaseDistanceDegrees) {
            return lockedAngle
        }

        return ORTHOGONAL_ANGLES.minByOrNull { angularDistance(rotation, it) }
            ?.takeIf { angularDistance(rotation, it) <= snapDistanceDegrees }
    }

    private fun angularDistance(first: Float, second: Float): Float {
        var difference = (first - second) % FULL_TURN
        if (difference > HALF_TURN) difference -= FULL_TURN
        if (difference < -HALF_TURN) difference += FULL_TURN
        return abs(difference)
    }

    private companion object {
        const val FULL_TURN = 360f
        const val HALF_TURN = FULL_TURN / 2f
        val ORTHOGONAL_ANGLES = floatArrayOf(-180f, -90f, 0f, 90f, 180f)
    }
}
