package de.zwegen.zpaint.tools.implementation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Snaps the text-box centre to quarter lines, or its outer bounds to image edges. */
class TextBoxGuideSnap {
    private val xState = AxisState()
    private val yState = AxisState()

    fun resolve(
        positionX: Float,
        positionY: Float,
        touchX: Float?,
        touchY: Float?,
        canvasWidth: Float,
        canvasHeight: Float,
        boxWidth: Float,
        boxHeight: Float,
        rotation: Float
    ): Result {
        val radians = Math.toRadians(rotation.toDouble())
        val halfWidth = (abs(cos(radians)) * boxWidth + abs(sin(radians)) * boxHeight).toFloat() / 2f
        val halfHeight = (abs(sin(radians)) * boxWidth + abs(cos(radians)) * boxHeight).toFloat() / 2f
        val x = resolveAxis(positionX, touchX, canvasWidth, halfWidth, xState)
        val y = resolveAxis(positionY, touchY, canvasHeight, halfHeight, yState)
        return Result(x.position, y.position, x.guide, y.guide)
    }

    fun reset() {
        xState.reset()
        yState.reset()
    }

    private fun resolveAxis(
        position: Float,
        touch: Float?,
        size: Float,
        halfExtent: Float,
        state: AxisState
    ): AxisResult {
        val distance = (size * 0.02f).coerceIn(6f, 80f)
        val candidates = buildList {
            if (halfExtent <= size / 2f) add(Target(halfExtent, 0f))
            add(Target(size * 0.25f, size * 0.25f))
            add(Target(size * 0.5f, size * 0.5f))
            add(Target(size * 0.75f, size * 0.75f))
            if (halfExtent <= size / 2f) add(Target(size - halfExtent, size))
        }

        if (state.locked != null && touch != null && abs(touch - state.lockTouch) >= distance) {
            state.suppressed = state.locked
            state.locked = null
        }
        if (state.suppressed != null &&
            abs(position - state.suppressed!!.position) > distance) {
            state.suppressed = null
        }
        val nearest = candidates.minByOrNull { abs(position - it.position) }
        val active = when {
            state.locked != null && abs(position - state.locked!!.position) <= distance ->
                state.locked
            nearest != null && abs(position - nearest.position) <= distance &&
                nearest != state.suppressed -> nearest
            else -> null
        }
        if (active != null && state.locked != active) {
            state.locked = active
            state.lockTouch = touch ?: position
        }
        if (active == null) state.locked = null
        val guide = active?.guide ?: nearest?.takeIf {
            abs(position - it.position) <= distance * 2f
        }?.guide
        return AxisResult(active?.position ?: position, guide)
    }

    private data class Target(val position: Float, val guide: Float)
    private data class AxisResult(val position: Float, val guide: Float?)
    private class AxisState {
        var locked: Target? = null
        var suppressed: Target? = null
        var lockTouch = 0f
        fun reset() {
            locked = null
            suppressed = null
        }
    }

    data class Result(val x: Float, val y: Float, val verticalGuide: Float?, val horizontalGuide: Float?)
}
