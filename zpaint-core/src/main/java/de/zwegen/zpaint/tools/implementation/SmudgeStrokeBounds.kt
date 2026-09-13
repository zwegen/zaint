package de.zwegen.zpaint.tools.implementation

import kotlin.math.ceil
import kotlin.math.floor

data class SmudgePoint(val x: Float, val y: Float)

/** The pixel rectangle needed to replay a smudge stroke without retaining its whole layer. */
data class SmudgeStrokeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top

    companion object {
        private const val ANTIALIAS_PADDING = 1f

        fun calculate(
            canvasWidth: Int,
            canvasHeight: Int,
            pointPath: List<SmudgePoint>,
            maxSize: Float
        ): SmudgeStrokeBounds {
            require(pointPath.isNotEmpty())
            val padding = maxSize / 2f + ANTIALIAS_PADDING
            val left = floor(pointPath.minOf { it.x } - padding).toInt().coerceIn(0, canvasWidth)
            val top = floor(pointPath.minOf { it.y } - padding).toInt().coerceIn(0, canvasHeight)
            val right = ceil(pointPath.maxOf { it.x } + padding).toInt().coerceIn(0, canvasWidth)
            val bottom = ceil(pointPath.maxOf { it.y } + padding).toInt().coerceIn(0, canvasHeight)
            return SmudgeStrokeBounds(left, top, right, bottom)
        }
    }
}
