package de.zwegen.zpaint.tools.implementation

import java.lang.Math.toRadians
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Converts a drag into the local resize movement of a rotated floating box. */
class FloatingBoxResizeDelta {
    fun resolve(
        deltaX: Float,
        deltaY: Float,
        rotation: Float,
        action: ResizeAction,
        width: Float,
        height: Float,
        keepAspectRatioOnCorners: Boolean = true
    ): FloatingBoxResizeMovement {
        val rotationRadians = toRadians(rotation.toDouble())
        var horizontal = cos(-rotationRadians) * deltaX - sin(-rotationRadians) * deltaY
        var vertical = sin(-rotationRadians) * deltaX + cos(-rotationRadians) * deltaY

        if (keepAspectRatioOnCorners) {
            when (action) {
                ResizeAction.TOPLEFT, ResizeAction.BOTTOMRIGHT -> {
                    if (abs(horizontal) > abs(vertical)) {
                        vertical = (width + horizontal) * height / width - height
                    } else {
                        horizontal = width * (height + vertical) / height - width
                    }
                }
                ResizeAction.TOPRIGHT, ResizeAction.BOTTOMLEFT -> {
                    if (abs(horizontal) > abs(vertical)) {
                        vertical = (width - horizontal) * height / width - height
                    } else {
                        horizontal = width * (height - vertical) / height - width
                    }
                }
                else -> Unit
            }
        }
        return FloatingBoxResizeMovement(horizontal.toFloat(), vertical.toFloat(), rotationRadians)
    }
}

data class FloatingBoxResizeMovement(
    val horizontal: Float,
    val vertical: Float,
    val rotationRadians: Double
)
