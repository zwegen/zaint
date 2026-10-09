package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.FilterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAreaTest {
    @Test
    fun `a fully rounded square has a circular mask`() {
        val area = FilterArea.circle(100f, 100f, 40f)

        assertEquals(40f, area.edgeDistance(100f, 100f), 0.001f)
        assertEquals(0f, area.edgeDistance(140f, 100f), 0.001f)
        assertTrue(area.edgeDistance(120f, 120f) > 0f)
        assertFalse(area.edgeDistance(140f, 140f) >= 0f)
    }

    @Test
    fun `removing rounding creates a rectangular mask`() {
        val area = FilterArea(100f, 100f, 120f, 60f, 0f)

        assertEquals(0f, area.edgeDistance(160f, 130f), 0.001f)
        assertTrue(area.edgeDistance(155f, 125f) > 0f)
        assertTrue(area.edgeDistance(165f, 135f) < 0f)
    }

    @Test
    fun `normalization keeps corner rounding inside the smallest side`() {
        val area = FilterArea(0f, 0f, 30f, 10f, 99f).normalized(16f)

        assertEquals(16f, area.height, 0f)
        assertEquals(8f, area.cornerRadius, 0f)
    }

    @Test
    fun `rotation rotates both the mask and its bounding box`() {
        val area = FilterArea(100f, 100f, 120f, 40f, 0f, rotationDegrees = 90f)

        assertTrue(area.edgeDistance(100f, 150f) > 0f)
        assertTrue(area.edgeDistance(150f, 100f) < 0f)
        assertEquals(20f, area.boundingHalfWidth, 0.001f)
        assertEquals(60f, area.boundingHalfHeight, 0.001f)
    }
}
