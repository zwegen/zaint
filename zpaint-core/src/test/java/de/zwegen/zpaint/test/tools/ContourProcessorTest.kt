package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.ContourProcessor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourProcessorTest {
    @Test
    fun `keeps a flat central object out of the border-connected background`() {
        val backgroundColor = 0xff888888.toInt()
        val objectColor = 0xffeeeeee.toInt()
        val pixels = pixelsOf(7, 7, backgroundColor).also { pixels ->
            for (y in 2..4) {
                for (x in 2..4) pixels[y * 7 + x] = objectColor
            }
        }

        val background = ContourProcessor.findBorderConnectedFlatRegions(pixels, 7, 7)

        assertTrue(background[0])
        assertFalse(background[3 * 7 + 3])
    }

    @Test
    fun `keeps a gently changing border-connected background together`() {
        val pixels = IntArray(7 * 5) { index ->
            val shade = 110 + (index % 7) * 4
            0xff000000.toInt() or (shade shl 16) or (shade shl 8) or shade
        }

        val background = ContourProcessor.findBorderConnectedFlatRegions(pixels, 7, 5)

        assertTrue(background.all { it })
    }

    @Test
    fun `closes a two-pixel gap only on a straight contour`() {
        val mask = maskOf(7, 3, 0 to 1, 1 to 1, 4 to 1, 5 to 1)

        val closed = ContourProcessor.closeShortAlignedGaps(mask, 7, 3)

        assertTrue(closed[1 * 7 + 2])
        assertTrue(closed[1 * 7 + 3])
    }

    @Test
    fun `does not connect nearby contour ends that are not aligned`() {
        val mask = maskOf(6, 4, 0 to 1, 3 to 2)

        val closed = ContourProcessor.closeShortAlignedGaps(mask, 6, 4)

        assertFalse(closed[1 * 6 + 1])
        assertFalse(closed[1 * 6 + 2])
    }

    @Test
    fun `does not close a three-pixel gap`() {
        val mask = maskOf(7, 3, 0 to 1, 4 to 1)

        val closed = ContourProcessor.closeShortAlignedGaps(mask, 7, 3)

        assertFalse(closed[1 * 7 + 1])
        assertFalse(closed[1 * 7 + 2])
        assertFalse(closed[1 * 7 + 3])
    }

    @Test
    fun `does not close a gap when either side belongs to a branch`() {
        val mask = maskOf(6, 3, 0 to 0, 0 to 1, 1 to 1, 3 to 1, 4 to 1)

        val closed = ContourProcessor.closeShortAlignedGaps(mask, 6, 3)

        assertFalse(closed[1 * 6 + 2])
    }

    @Test
    fun `fills a one-pixel hole with eight black neighbours`() {
        val mask = filledMask(5, 5).also { it[2 * 5 + 2] = false }

        val filled = ContourProcessor.fillSupportedGaps(mask, 5, 5)

        assertTrue(filled[2 * 5 + 2])
    }

    @Test
    fun `fills a two-by-one rill with seven black neighbours per pixel`() {
        val mask = filledMask(6, 6).also {
            it[2 * 6 + 2] = false
            it[2 * 6 + 3] = false
        }

        val filled = ContourProcessor.fillSupportedGaps(mask, 6, 6)

        assertTrue(filled[2 * 6 + 2])
        assertTrue(filled[2 * 6 + 3])
    }

    @Test
    fun `fills a one-pixel-wide rill with six black neighbours`() {
        val mask = filledMask(7, 7).also {
            for (y in 2..4) it[y * 7 + 3] = false
        }

        val filled = ContourProcessor.fillSupportedGaps(mask, 7, 7)

        assertTrue(filled[2 * 7 + 3])
        assertTrue(filled[3 * 7 + 3])
        assertTrue(filled[4 * 7 + 3])
    }

    @Test
    fun `fills a two-by-two hole with five black neighbours per pixel`() {
        val twoByTwo = filledMask(6, 6).also {
            it[2 * 6 + 2] = false
            it[2 * 6 + 3] = false
            it[3 * 6 + 2] = false
            it[3 * 6 + 3] = false
        }

        val filled = ContourProcessor.fillSupportedGaps(twoByTwo, 6, 6)

        assertTrue(filled[2 * 6 + 2])
        assertTrue(filled[2 * 6 + 3])
        assertTrue(filled[3 * 6 + 2])
        assertTrue(filled[3 * 6 + 3])
    }

    @Test
    fun `keeps wide open areas unchanged`() {
        val wideOpen = filledMask(7, 7).also {
            for (y in 0..3) for (x in 2..4) it[y * 7 + x] = false
        }

        val wideOpenResult = ContourProcessor.fillSupportedGaps(wideOpen, 7, 7)

        assertFalse(wideOpenResult[0 * 7 + 3])
        assertFalse(wideOpenResult[1 * 7 + 3])
    }

    @Test
    fun `removes a black protrusion whose neighbours stay connected`() {
        val mask = maskOf(5, 5, 1 to 1, 2 to 1, 3 to 1, 2 to 2)

        val refined = ContourProcessor.removeUnsupportedBlackPixels(mask, 5, 5)

        assertFalse(refined[2 * 5 + 2])
        assertTrue(refined[1 * 5 + 2])
    }

    @Test
    fun `keeps a thin line and its endpoint`() {
        val mask = maskOf(5, 6, 2 to 1, 2 to 2, 2 to 3, 2 to 4)

        val refined = ContourProcessor.removeUnsupportedBlackPixels(mask, 5, 6)

        assertTrue(refined[2 * 5 + 2])
        assertTrue(refined[4 * 5 + 2])
    }

    private fun maskOf(width: Int, height: Int, vararg points: Pair<Int, Int>): BooleanArray =
        BooleanArray(width * height).also { mask ->
            points.forEach { (x, y) -> mask[y * width + x] = true }
        }

    private fun filledMask(width: Int, height: Int): BooleanArray = BooleanArray(width * height) { true }

    private fun pixelsOf(width: Int, height: Int, color: Int): IntArray = IntArray(width * height) { color }
}
