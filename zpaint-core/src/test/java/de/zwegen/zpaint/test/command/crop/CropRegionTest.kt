package de.zwegen.zpaint.test.command.crop

import de.zwegen.zpaint.command.crop.CropRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropRegionTest {
    @Test
    fun `returns the inclusive selected size`() {
        val size = CropRegion(left = 2, top = 3, right = 7, bottom = 9)
            .targetSize(documentWidth = 10, documentHeight = 12, maximumBitmapResolution = 100)

        assertEquals(6, size?.width)
        assertEquals(7, size?.height)
    }

    @Test
    fun `rejects the unchanged document and an oversized crop`() {
        assertNull(CropRegion(0, 0, 9, 11).targetSize(10, 12, 1000))
        assertNull(CropRegion(0, 0, 9, 11).targetSize(10, 12, 100))
    }

    @Test
    fun `keeps partially outside selections so transparent margins are preserved`() {
        val size = CropRegion(left = -2, top = -1, right = 4, bottom = 3)
            .targetSize(documentWidth = 10, documentHeight = 12, maximumBitmapResolution = 100)

        assertEquals(7, size?.width)
        assertEquals(5, size?.height)
    }
}
