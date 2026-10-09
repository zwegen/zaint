package de.zwegen.zpaint.test.junit.algorithm

import android.graphics.Bitmap
import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.zwegen.zpaint.tools.helper.ZaintScanlineFill
import de.zwegen.zpaint.tools.implementation.MAX_ABSOLUTE_TOLERANCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val HALF_TOLERANCE = MAX_ABSOLUTE_TOLERANCE / 2.0f

@RunWith(AndroidJUnit4::class)
class FillAlgorithmTest {
    @Test
    fun testFillAlgorithmMembers() {
        val width = 10
        val height = 20
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val clickedPixel = Point(width / 2, height / 2)
        val targetColor = 16_777_215
        val replacementColor = 0
        val fillAlgorithm = ZaintScanlineFill()
        fillAlgorithm.setParameters(
            bitmap,
            clickedPixel,
            targetColor,
            replacementColor,
            HALF_TOLERANCE
        )
        val algorithmPixels = fillAlgorithm.pixels
        assertEquals("Wrong array size", height, algorithmPixels.size)
        assertEquals("Wrong array size", width, algorithmPixels[0].size)
        val algorithmTargetColor = fillAlgorithm.targetColor
        val algorithmReplacementColor = fillAlgorithm.colorToBeReplaced
        val algorithmColorTolerance = fillAlgorithm.colorToleranceThresholdSquared.toFloat()
        assertEquals(
            "Wrong target color",
            targetColor,
            algorithmTargetColor
        )
        assertEquals(
            "Wrong replacement color",
            replacementColor,
            algorithmReplacementColor
        )
        assertEquals(
            "Wrong color tolerance",
            HALF_TOLERANCE * HALF_TOLERANCE,
            algorithmColorTolerance
        )
        val algorithmClickedPixel = fillAlgorithm.clickedPixel
        assertEquals("Wrong point for clicked pixel", clickedPixel, algorithmClickedPixel)
        val algorithmRanges = fillAlgorithm.ranges
        assertTrue("Queue for ranges should be empty", algorithmRanges.isEmpty())
    }
}
