package de.zwegen.zpaint.test.tools.implementation

import android.graphics.Rect
import de.zwegen.zpaint.tools.implementation.FillSelectionMask
import de.zwegen.zpaint.tools.implementation.ImageFillRegion
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FillSelectionMaskTest {
    @Test
    fun imageMaskSmoothsOnlyTheSelectedInnerEdge() {
        val width = 5
        val selected = BooleanArray(width * 5) { index -> index % width in 1..3 }
        val region = ImageFillRegion(width, selected, Rect(1, 0, 4, 5))

        val mask = FillSelectionMask.antialiasedImageMask(listOf(region))

        assertEquals(0, mask[2 * width])
        assertEquals(191, mask[2 * width + 1])
        assertEquals(255, mask[2 * width + 2])
        assertEquals(191, mask[2 * width + 3])
        assertEquals(255, mask[1]) // The canvas boundary stays fully opaque.
    }
}
