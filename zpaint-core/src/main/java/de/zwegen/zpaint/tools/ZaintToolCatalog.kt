/*
 * Zaint: Active tool catalog.
 *
 * The catalog is deliberately Android-free. It is the single source for
 * current filter tool membership, used by both the UI interaction policy and
 * the tool factory.
 */
package de.zwegen.zpaint.tools

object ZaintToolCatalog {
    val adjustableFilterTools: Set<ZaintToolKind> = setOf(
        ZaintToolKind.FILTER_BRIGHTNESS,
        ZaintToolKind.FILTER_CONTRAST,
        ZaintToolKind.FILTER_BLUR,
        ZaintToolKind.FILTER_BLOOM,
        ZaintToolKind.FILTER_MEDIAN,
        ZaintToolKind.FILTER_BILATERAL,
        ZaintToolKind.FILTER_SATURATION,
        ZaintToolKind.FILTER_HUE,
        ZaintToolKind.FILTER_TEMPERATURE,
        ZaintToolKind.FILTER_HIGHLIGHTS,
        ZaintToolKind.FILTER_SHADOWS,
        ZaintToolKind.FILTER_SEPIA,
        ZaintToolKind.FILTER_SHARPEN,
        ZaintToolKind.FILTER_VIGNETTE,
        ZaintToolKind.FILTER_CLARITY,
        ZaintToolKind.FILTER_EXPOSURE,
        ZaintToolKind.FILTER_VIBRANCE,
        ZaintToolKind.FILTER_PIXEL,
        ZaintToolKind.FILTER_SPLASH
    )

    val filterTools: Set<ZaintToolKind> = adjustableFilterTools + ZaintToolKind.FILTER_INVERT

    fun isFilter(toolType: ZaintToolKind): Boolean = toolType in filterTools

    fun hasAdjustableFilterOptions(toolType: ZaintToolKind): Boolean =
        toolType in adjustableFilterTools
}
