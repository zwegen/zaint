package de.zwegen.zpaint.tools.implementation

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

/** A movable filter mask with independently sized sides and adjustable corner rounding. */
data class FilterArea(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
    val rotationDegrees: Float = 0f
) {
    val halfWidth: Float
        get() = width / 2f

    val halfHeight: Float
        get() = height / 2f

    val maximumCornerRadius: Float
        get() = min(halfWidth, halfHeight)

    val boundingHalfWidth: Float
        get() {
            val radians = Math.toRadians(rotationDegrees.toDouble())
            return abs(cos(radians)).toFloat() * halfWidth + abs(sin(radians)).toFloat() * halfHeight
        }

    val boundingHalfHeight: Float
        get() {
            val radians = Math.toRadians(rotationDegrees.toDouble())
            return abs(sin(radians)).toFloat() * halfWidth + abs(cos(radians)).toFloat() * halfHeight
        }

    fun normalized(minimumSize: Float): FilterArea {
        val normalizedWidth = width.coerceAtLeast(minimumSize)
        val normalizedHeight = height.coerceAtLeast(minimumSize)
        return copy(
            width = normalizedWidth,
            height = normalizedHeight,
            cornerRadius = cornerRadius.coerceIn(0f, min(normalizedWidth, normalizedHeight) / 2f)
        )
    }

    fun scaled(scale: Float): FilterArea = copy(
        centerX = centerX * scale,
        centerY = centerY * scale,
        width = width * scale,
        height = height * scale,
        cornerRadius = cornerRadius * scale
    )

    /** Returns the distance to the mask edge: positive inside, zero on it, negative outside. */
    fun edgeDistance(x: Float, y: Float): Float {
        val radius = cornerRadius.coerceIn(0f, maximumCornerRadius)
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val offsetX = x - centerX
        val offsetY = y - centerY
        val rotatedX = (offsetX * cos(radians) + offsetY * sin(radians)).toFloat()
        val rotatedY = (-offsetX * sin(radians) + offsetY * cos(radians)).toFloat()
        val localX = abs(rotatedX) - halfWidth + radius
        val localY = abs(rotatedY) - halfHeight + radius
        val outsideDistance = hypot(max(localX, 0f), max(localY, 0f))
        val insideDistance = min(max(localX, localY), 0f)
        return radius - outsideDistance - insideDistance
    }

    companion object {
        fun circle(centerX: Float, centerY: Float, radius: Float): FilterArea =
            FilterArea(centerX, centerY, radius * 2f, radius * 2f, radius)
    }
}
