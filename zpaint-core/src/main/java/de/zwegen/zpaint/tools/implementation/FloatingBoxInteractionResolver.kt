package de.zwegen.zpaint.tools.implementation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Chooses the action for a pointer on a rotated floating box. */
class FloatingBoxInteractionResolver(
    private val resizeDirection: FloatingBoxResizeDirection = FloatingBoxResizeDirection()
) {
    fun resolve(
        pointX: Float,
        pointY: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        resizeMargin: Float,
        rotationEnabled: Boolean,
        rotationSymbolDistance: Float
    ): FloatingBoxInteraction {
        val radians = rotation * PI.toFloat() / 180f
        val localX = centerX + cos(-radians) * (pointX - centerX) - sin(-radians) * (pointY - centerY)
        val localY = centerY + sin(-radians) * (pointX - centerX) + cos(-radians) * (pointY - centerY)

        if (isInside(localX, localY, centerX, centerY, width, height, -resizeMargin)) {
            return FloatingBoxInteraction(FloatingBoxAction.MOVE, ResizeAction.NONE)
        }
        if (isInside(localX, localY, centerX, centerY, width, height, resizeMargin)) {
            return FloatingBoxInteraction(
                FloatingBoxAction.RESIZE,
                resizeDirection.resolve(localX, localY, centerX, centerY, width, height, resizeMargin)
            )
        }
        if (rotationEnabled && isOnRotationPoint(
                localX,
                localY,
                centerX,
                centerY,
                width,
                height,
                rotationSymbolDistance
            )
        ) {
            return FloatingBoxInteraction(FloatingBoxAction.ROTATE, ResizeAction.NONE)
        }
        return FloatingBoxInteraction(FloatingBoxAction.MOVE, ResizeAction.NONE)
    }

    private fun isInside(
        pointX: Float,
        pointY: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        margin: Float
    ): Boolean =
        pointX < centerX + width / 2f + margin && pointX > centerX - width / 2f - margin &&
            pointY < centerY + height / 2f + margin && pointY > centerY - height / 2f - margin

    private fun isOnRotationPoint(
        pointX: Float,
        pointY: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        distance: Float
    ): Boolean {
        val offsetX = width / 2f + distance / 2f
        val offsetY = height / 2f + distance / 2f
        val halfDistance = distance / 2f
        return arrayOf(
            centerX - offsetX to centerY - offsetY,
            centerX + offsetX to centerY - offsetY,
            centerX - offsetX to centerY + offsetY,
            centerX + offsetX to centerY + offsetY
        ).any { (rotationX, rotationY) ->
            pointX > rotationX - halfDistance && pointX < rotationX + halfDistance &&
                pointY > rotationY - halfDistance && pointY < rotationY + halfDistance
        }
    }
}

data class FloatingBoxInteraction(
    val action: FloatingBoxAction,
    val resizeAction: ResizeAction
)

enum class FloatingBoxAction {
    NONE, MOVE, RESIZE, ROTATE
}
