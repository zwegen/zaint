package de.zwegen.zpaint.tools.implementation

import kotlin.math.atan2

/** Pure rotation rule for the floating selection box. */
class FloatingBoxRotation {
    fun afterDrag(
        currentX: Float,
        currentY: Float,
        deltaX: Float,
        deltaY: Float,
        centerX: Float,
        centerY: Float,
        currentRotation: Float
    ): Float {
        val previousAngle = atan2(
            (currentY - deltaY - centerY).toDouble(),
            (currentX - deltaX - centerX).toDouble()
        )
        val currentAngle = atan2(
            (currentY - centerY).toDouble(),
            (currentX - centerX).toDouble()
        )
        var rotation = currentRotation - Math.toDegrees(previousAngle - currentAngle).toFloat() + 360f
        rotation %= 360f
        return if (rotation > 180f) rotation - 360f else rotation
    }
}
