package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.VisiblePixelOverlap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisiblePixelOverlapTest {
    @Test
    fun detectsEvenAlmostTransparentPixelsAsAnOverlap() {
        assertTrue(
            VisiblePixelOverlap.hasOverlappingVisiblePixels(
                intArrayOf(0x00000000, 0xFFFFFFFF.toInt()),
                intArrayOf(0x01000000, 0x01000000)
            )
        )
    }

    @Test
    fun ignoresPixelsThatAreTransparentInEitherDrawing() {
        assertFalse(
            VisiblePixelOverlap.hasOverlappingVisiblePixels(
                intArrayOf(0x00000000, 0xFFFFFFFF.toInt()),
                intArrayOf(0xFFFFFFFF.toInt(), 0x00000000)
            )
        )
    }
}
