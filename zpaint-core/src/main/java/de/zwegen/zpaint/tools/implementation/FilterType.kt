/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import de.zwegen.zpaint.tools.ZaintToolKind

enum class FilterType {
    INVERT,
    BRIGHTNESS,
    CONTRAST,
    BLUR,
    BLOOM,
    MEDIAN,
    BILATERAL,
    SATURATION,
    TEMPERATURE,
    HIGHLIGHTS,
    SHADOWS,
    SEPIA,
    SHARPEN,
    VIGNETTE,
    CLARITY,
    EXPOSURE,
    VIBRANCE,
    PIXEL,
    HUE,
    SPLASH,
    CONTOURS,
    CONTOUR_ERASE;

    companion object {
        fun fromToolType(toolType: ZaintToolKind): FilterType = when (toolType) {
            ZaintToolKind.FILTER_INVERT -> INVERT
            ZaintToolKind.FILTER_BRIGHTNESS -> BRIGHTNESS
            ZaintToolKind.FILTER_CONTRAST -> CONTRAST
            ZaintToolKind.FILTER_BLUR -> BLUR
            ZaintToolKind.FILTER_BLOOM -> BLOOM
            ZaintToolKind.FILTER_MEDIAN -> MEDIAN
            ZaintToolKind.FILTER_BILATERAL -> BILATERAL
            ZaintToolKind.FILTER_SATURATION -> SATURATION
            ZaintToolKind.FILTER_TEMPERATURE -> TEMPERATURE
            ZaintToolKind.FILTER_HIGHLIGHTS -> HIGHLIGHTS
            ZaintToolKind.FILTER_SHADOWS -> SHADOWS
            ZaintToolKind.FILTER_SEPIA -> SEPIA
            ZaintToolKind.FILTER_SHARPEN -> SHARPEN
            ZaintToolKind.FILTER_VIGNETTE -> VIGNETTE
            ZaintToolKind.FILTER_CLARITY -> CLARITY
            ZaintToolKind.FILTER_EXPOSURE -> EXPOSURE
            ZaintToolKind.FILTER_VIBRANCE -> VIBRANCE
            ZaintToolKind.FILTER_PIXEL -> PIXEL
            ZaintToolKind.FILTER_HUE -> HUE
            ZaintToolKind.FILTER_SPLASH -> SPLASH
            ZaintToolKind.FILTER_CONTOURS -> CONTOURS
            else -> throw IllegalArgumentException("Tool is not a filter: $toolType")
        }
    }
}
