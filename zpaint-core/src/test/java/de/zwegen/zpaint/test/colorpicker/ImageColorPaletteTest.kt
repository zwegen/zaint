package de.zwegen.zpaint.test.colorpicker

import de.zwegen.zpaint.colorpicker.ImageColorPalette
import org.junit.Assert.*
import org.junit.Test

class ImageColorPaletteTest {
    @Test fun findsFiveDistinctDominantColorsInPopulationOrder() {
        val colors = listOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(),
            0xffffff00.toInt(), 0xffff00ff.toInt())
        val pixels = colors.flatMapIndexed { index, color -> List(5 - index) { color } }.toIntArray()
        assertEquals(colors, ImageColorPalette.extract(pixels))
    }

    @Test fun ignoresTransparentPixelsAndDoesNotInventColorsForASingleColorImage() {
        val blue = 0xff0000ff.toInt()
        assertEquals(listOf(blue), ImageColorPalette.extract(intArrayOf(0x00ff0000, 0x00000000, blue, blue)))
        assertTrue(ImageColorPalette.extract(intArrayOf(0, 0x7f00ff00)).isEmpty())
    }

    @Test fun mergesCloseShadesAndRespectsTheRequestedLimit() {
        val colors = ImageColorPalette.extract(intArrayOf(0xffff0000.toInt(), 0xffef0000.toInt(),
            0xff00ff00.toInt(), 0xff0000ff.toInt()))
        assertEquals(3, colors.size)
        assertEquals(2, ImageColorPalette.extract(intArrayOf(0xffff0000.toInt(),
            0xff00ff00.toInt(), 0xff0000ff.toInt()), 2).size)
    }

    @Test fun emptyInputHasNoSuggestions() {
        assertTrue(ImageColorPalette.extract(intArrayOf()).isEmpty())
        assertTrue(ImageColorPalette.extract(intArrayOf(-1), 0).isEmpty())
    }
    @Test fun filtersSimilarGreenShadesWithoutInventingExtraColors() {
        val green = 0xff308430.toInt()
        val nearGreen = 0xff30b430.toInt()
        val blue = 0xff0000ff.toInt()
        val magenta = 0xfff000c0.toInt()
        val brown = 0xffb07030.toInt()
        val pixels = (List(100) { green } + List(90) { nearGreen } +
            List(9) { blue } + List(8) { magenta } + List(7) { brown }).toIntArray()
        val result = ImageColorPalette.extract(pixels)
        assertEquals(green, result.first())
        assertEquals(setOf(green, blue, magenta, brown), result.toSet())
        assertFalse(result.contains(nearGreen))
    }

    @Test fun favorsDistinctImageColorOverAnotherFrequentShade() {
        val green = 0xff308430.toInt()
        val lighterGreen = 0xff30d430.toInt()
        val blue = 0xff0000ff.toInt()
        val pixels = (List(100) { green } + List(90) { lighterGreen } + List(10) { blue }).toIntArray()
        assertEquals(listOf(green, blue), ImageColorPalette.extract(pixels, 2))
    }

    @Test fun similarOnlyPictureReturnsFewerSuggestionsInsteadOfDuplicates() {
        val green = 0xff308430.toInt()
        val pixels = (List(100) { green } + List(90) { 0xff30a430.toInt() } +
            List(80) { 0xff30c430.toInt() }).toIntArray()
        assertEquals(listOf(green), ImageColorPalette.extract(pixels))
    }

}
