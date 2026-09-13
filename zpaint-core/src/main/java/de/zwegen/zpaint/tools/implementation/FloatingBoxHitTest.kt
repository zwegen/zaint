package de.zwegen.zpaint.tools.implementation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pure hit test for a rotated floating box. */
class FloatingBoxHitTest {
    fun contains(
        pointX: Float,
        pointY: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float
    ): Boolean {
        val relativeX = pointX - centerX
        val relativeY = pointY - centerY
        val radians = -(rotation * PI.toFloat() / 180f)
        val localX = relativeX * cos(radians) - relativeY * sin(radians)
        val localY = relativeX * sin(radians) + relativeY * cos(radians)

        return localX > -width / 2f && localX < width / 2f &&
            localY > -height / 2f && localY < height / 2f
    }
}
