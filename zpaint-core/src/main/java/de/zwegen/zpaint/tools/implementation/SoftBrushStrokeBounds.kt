package de.zwegen.zpaint.tools.implementation

import kotlin.math.ceil
import kotlin.math.floor

data class SoftBrushStrokeBounds(
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
        private const val BLUR_SIGMA_COVERAGE = 3f
        private const val ANTIALIAS_PADDING = 1f

        fun calculate(
            canvasWidth: Int,
            canvasHeight: Int,
            pathLeft: Float,
            pathTop: Float,
            pathRight: Float,
            pathBottom: Float,
            strokeWidth: Float,
            blurRadius: Float
        ): SoftBrushStrokeBounds {
            val padding = strokeWidth / 2f + blurRadius * BLUR_SIGMA_COVERAGE + ANTIALIAS_PADDING
            return SoftBrushStrokeBounds(
                floor(pathLeft - padding).toInt().coerceIn(0, canvasWidth),
                floor(pathTop - padding).toInt().coerceIn(0, canvasHeight),
                ceil(pathRight + padding).toInt().coerceIn(0, canvasWidth),
                ceil(pathBottom + padding).toInt().coerceIn(0, canvasHeight)
            )
        }
    }
}
