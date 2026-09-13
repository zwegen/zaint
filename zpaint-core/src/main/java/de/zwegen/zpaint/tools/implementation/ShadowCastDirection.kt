package de.zwegen.zpaint.tools.implementation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** The four diagonal directions used by the direct shadow handles. */
enum class ShadowCastDirection(
    val stepX: Int,
    val stepY: Int,
    val iconLevel: Int
) {
    DOWN_RIGHT(1, 1, 8_750),
    DOWN_LEFT(-1, 1, 1_250),
    UP_LEFT(-1, -1, 3_750),
    UP_RIGHT(1, -1, 6_250);

    /** Angle in canvas coordinates (positive Y points down). */
    val angleRadians: Float get() = atan2(stepY.toFloat(), stepX.toFloat())

    companion object {
        /** Returns the closest of the four diagonal snap directions in canvas coordinates. */
        fun nearestTo(deltaX: Float, deltaY: Float): ShadowCastDirection =
            values().maxByOrNull { direction ->
                val length = kotlin.math.hypot(direction.stepX.toDouble(), direction.stepY.toDouble()).toFloat()
                (deltaX * direction.stepX + deltaY * direction.stepY) / length
            } ?: DOWN_RIGHT

        fun nearestToAngle(angleRadians: Float): ShadowCastDirection =
            values().minByOrNull { angularDistance(angleRadians, it.angleRadians) } ?: DOWN_RIGHT

        fun angularDistance(first: Float, second: Float): Float {
            var difference = (first - second) % (2f * PI.toFloat())
            if (difference > PI) difference -= 2f * PI.toFloat()
            if (difference < -PI) difference += 2f * PI.toFloat()
            return abs(difference)
        }
    }

    fun next(): ShadowCastDirection? = when (this) {
        DOWN_RIGHT -> DOWN_LEFT
        DOWN_LEFT -> UP_LEFT
        UP_LEFT -> UP_RIGHT
        UP_RIGHT -> null
    }
}
