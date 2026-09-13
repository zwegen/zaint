package de.zwegen.zpaint.tools.implementation

import kotlin.math.cos
import kotlin.math.sin

/** Applies a local resize movement to the size and center of a floating box. */
class FloatingBoxResizeApplication {
    fun apply(
        state: FloatingBoxResizeState,
        movement: FloatingBoxResizeMovement,
        action: ResizeAction,
        maximumWidth: Float,
        maximumHeight: Float,
        limitBorderRatio: Boolean
    ): FloatingBoxResizeState = applyWidth(
        applyHeight(state, movement, action, maximumHeight, limitBorderRatio),
        movement,
        action,
        maximumWidth,
        limitBorderRatio
    )

    private fun applyHeight(
        state: FloatingBoxResizeState,
        movement: FloatingBoxResizeMovement,
        action: ResizeAction,
        maximumHeight: Float,
        limitBorderRatio: Boolean
    ): FloatingBoxResizeState {
        val height = when (action) {
            ResizeAction.TOP, ResizeAction.TOPRIGHT, ResizeAction.TOPLEFT -> state.height - movement.vertical
            ResizeAction.BOTTOM, ResizeAction.BOTTOMLEFT, ResizeAction.BOTTOMRIGHT -> state.height + movement.vertical
            else -> return state
        }
        if (limitBorderRatio && height > maximumHeight) {
            return state.copy(height = maximumHeight)
        }
        val centerMoveX = movement.vertical / 2f * sin(movement.rotationRadians).toFloat()
        val centerMoveY = movement.vertical / 2f * cos(movement.rotationRadians).toFloat()
        return state.copy(
            centerX = state.centerX - centerMoveX,
            centerY = state.centerY + centerMoveY,
            height = height
        )
    }

    private fun applyWidth(
        state: FloatingBoxResizeState,
        movement: FloatingBoxResizeMovement,
        action: ResizeAction,
        maximumWidth: Float,
        limitBorderRatio: Boolean
    ): FloatingBoxResizeState {
        val width = when (action) {
            ResizeAction.LEFT, ResizeAction.TOPLEFT, ResizeAction.BOTTOMLEFT -> state.width - movement.horizontal
            ResizeAction.RIGHT, ResizeAction.TOPRIGHT, ResizeAction.BOTTOMRIGHT -> state.width + movement.horizontal
            else -> return state
        }
        if (limitBorderRatio && width > maximumWidth) {
            return state.copy(width = maximumWidth)
        }
        val centerMoveX = movement.horizontal / 2f * cos(movement.rotationRadians).toFloat()
        val centerMoveY = movement.horizontal / 2f * sin(movement.rotationRadians).toFloat()
        return state.copy(
            centerX = state.centerX + centerMoveX,
            centerY = state.centerY + centerMoveY,
            width = width
        )
    }
}

data class FloatingBoxResizeState(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)
