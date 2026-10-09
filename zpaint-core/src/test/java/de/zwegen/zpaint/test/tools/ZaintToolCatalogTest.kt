package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.ZaintToolCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZaintToolCatalogTest {
    @Test
    fun allCurrentFiltersAreRegistered() {
        val visibleFilters = setOf(
            ZaintToolKind.FILTER_INVERT,
            ZaintToolKind.FILTER_BRIGHTNESS,
            ZaintToolKind.FILTER_CONTRAST,
            ZaintToolKind.FILTER_BLUR,
            ZaintToolKind.FILTER_BLOOM,
            ZaintToolKind.FILTER_MEDIAN,
            ZaintToolKind.FILTER_BILATERAL,
            ZaintToolKind.FILTER_SATURATION,
            ZaintToolKind.FILTER_HUE,
            ZaintToolKind.FILTER_VIBRANCE,
            ZaintToolKind.FILTER_TEMPERATURE,
            ZaintToolKind.FILTER_HIGHLIGHTS,
            ZaintToolKind.FILTER_SHADOWS,
            ZaintToolKind.FILTER_SEPIA,
            ZaintToolKind.FILTER_SHARPEN,
            ZaintToolKind.FILTER_VIGNETTE,
            ZaintToolKind.FILTER_CLARITY,
            ZaintToolKind.FILTER_EXPOSURE,
            ZaintToolKind.FILTER_PIXEL
        )

        assertTrue(ZaintToolCatalog.filterTools.containsAll(visibleFilters))
        assertFalse(ZaintToolCatalog.hasAdjustableFilterOptions(ZaintToolKind.FILTER_INVERT))
        assertTrue(ZaintToolCatalog.hasAdjustableFilterOptions(ZaintToolKind.FILTER_PIXEL))
    }
}
