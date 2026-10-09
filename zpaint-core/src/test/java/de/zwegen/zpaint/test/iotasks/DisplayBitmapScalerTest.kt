package de.zwegen.zpaint.test.iotasks

import de.zwegen.zpaint.iotasks.DisplayBitmapScaler
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayBitmapScalerTest {
    @Test
    fun calculateTargetSize_scalesLandscapeImageToDisplayWidthWithoutCropping() {
        val target = DisplayBitmapScaler.calculateTargetSize(
            sourceWidth = 4000,
            sourceHeight = 3000,
            maxWidth = 1080,
            maxHeight = 1920
        )

        assertEquals(1080, target.width)
        assertEquals(810, target.height)
    }

    @Test
    fun calculateTargetSize_scalesPortraitImageToDisplayHeightWithoutCropping() {
        val target = DisplayBitmapScaler.calculateTargetSize(
            sourceWidth = 3000,
            sourceHeight = 4000,
            maxWidth = 1920,
            maxHeight = 1080
        )

        assertEquals(810, target.width)
        assertEquals(1080, target.height)
    }

    @Test
    fun calculateTargetSize_scalesPortraitImageToVisibleDrawingAreaInsteadOfFullDisplay() {
        val target = DisplayBitmapScaler.calculateTargetSizeForVisibleDrawingSurface(
            sourceWidth = 3000,
            sourceHeight = 4000,
            maxWidth = 1080,
            maxHeight = 1600
        )

        assertEquals(1080, target.width)
        assertEquals(1440, target.height)
    }

    @Test
    fun calculateTargetSize_scalesSquareImageToVisibleDrawingSurfaceWithoutCropping() {
        val target = DisplayBitmapScaler.calculateTargetSizeForVisibleDrawingSurface(
            sourceWidth = 4000,
            sourceHeight = 4000,
            maxWidth = 1080,
            maxHeight = 1600
        )

        assertEquals(1080, target.width)
        assertEquals(1080, target.height)
    }

    @Test
    fun calculateTargetSize_keepsSmallImageUnchanged() {
        val target = DisplayBitmapScaler.calculateTargetSizeForVisibleDrawingSurface(
            sourceWidth = 800,
            sourceHeight = 600,
            maxWidth = 1080,
            maxHeight = 1600
        )

        assertEquals(800, target.width)
        assertEquals(600, target.height)
    }

    @Test
    fun calculateInSampleSize_keepsDecodedLandscapeLargeEnoughForPerspectiveFit() {
        val target = DisplayBitmapScaler.calculateTargetSizeForVisibleDrawingSurface(
            sourceWidth = 4000,
            sourceHeight = 3000,
            maxWidth = 1080,
            maxHeight = 1600
        )

        assertEquals(2, DisplayBitmapScaler.calculateInSampleSize(4000, 3000, target))
    }

    @Test
    fun calculateInSampleSize_keepsDecodedPortraitLargeEnoughForPerspectiveFit() {
        val target = DisplayBitmapScaler.calculateTargetSizeForVisibleDrawingSurface(
            sourceWidth = 3000,
            sourceHeight = 4000,
            maxWidth = 1080,
            maxHeight = 1600
        )

        assertEquals(2, DisplayBitmapScaler.calculateInSampleSize(3000, 4000, target))
    }
}
