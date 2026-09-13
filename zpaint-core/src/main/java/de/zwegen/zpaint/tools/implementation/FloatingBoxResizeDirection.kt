package de.zwegen.zpaint.tools.implementation

/** Determines which frame edge or corner changes the size of a floating box. */
class FloatingBoxResizeDirection {
    fun resolve(
        pointX: Float,
        pointY: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        margin: Float
    ): ResizeAction {
        var action = ResizeAction.NONE
        if (pointX < centerX - width / 2f + margin) {
            action = ResizeAction.LEFT
        } else if (pointX > centerX + width / 2f - margin) {
            action = ResizeAction.RIGHT
        }

        return when {
            pointY < centerY - height / 2f + margin -> when (action) {
                ResizeAction.LEFT -> ResizeAction.TOPLEFT
                ResizeAction.RIGHT -> ResizeAction.TOPRIGHT
                else -> ResizeAction.TOP
            }
            pointY > centerY + height / 2f - margin -> when (action) {
                ResizeAction.LEFT -> ResizeAction.BOTTOMLEFT
                ResizeAction.RIGHT -> ResizeAction.BOTTOMRIGHT
                else -> ResizeAction.BOTTOM
            }
            else -> action
        }
    }
}

enum class ResizeAction {
    NONE, TOP, RIGHT, BOTTOM, LEFT, TOPLEFT, TOPRIGHT, BOTTOMLEFT, BOTTOMRIGHT
}
