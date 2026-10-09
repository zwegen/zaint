package de.zwegen.zpaint.test.controller

import de.zwegen.zpaint.controller.ToolInteractionPolicy
import de.zwegen.zpaint.tools.ZaintToolKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolInteractionPolicyTest {
    @Test
    fun visibleAdjustableFiltersKeepTheirExplicitApplyFlow() {
        val filters = setOf(
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
            ZaintToolKind.FILTER_PIXEL
        )

        assertTrue(ToolInteractionPolicy.explicitApplyTools.containsAll(filters))
    }

    @Test
    fun onlySelectionOffersAManualNewLayerAction() {
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.TEXT))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.ICON))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.IMPORTPNG))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.SPEECH_BUBBLE))
        assertTrue(ToolInteractionPolicy.createsLayer(ZaintToolKind.SELECTION))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.SHADOW))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.FILTER_PIXEL))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.BRUSH))
    }

    @Test
    fun alignRequiresExplicitApply() {
        assertTrue(ToolInteractionPolicy.requiresExplicitApply(ZaintToolKind.ALIGN))
    }

    @Test
    fun speechBubbleRequiresExplicitApplyWithoutAManualNewLayerAction() {
        assertTrue(ToolInteractionPolicy.requiresExplicitApply(ZaintToolKind.SPEECH_BUBBLE))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.SPEECH_BUBBLE))
    }

    @Test
    fun warpAppliesEachStrokeDirectlyWithoutAnApplyButton() {
        assertFalse(ToolInteractionPolicy.requiresExplicitApply(ZaintToolKind.WARP))
        assertFalse(ToolInteractionPolicy.createsLayer(ZaintToolKind.WARP))
    }
}
