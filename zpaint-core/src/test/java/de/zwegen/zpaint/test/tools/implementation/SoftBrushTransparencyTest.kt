package de.zwegen.zpaint.tools.implementation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBrushTransparencyTest {
    @Test
    fun transparentColorUsesDirectLayerClear() {
        assertTrue(softBrushUsesDirectLayerClear(0x00112233))
    }

    @Test
    fun visibleColorKeepsBitmapStampPath() {
        assertFalse(softBrushUsesDirectLayerClear(0x01112233))
        assertFalse(softBrushUsesDirectLayerClear(0xFF112233.toInt()))
    }
}
