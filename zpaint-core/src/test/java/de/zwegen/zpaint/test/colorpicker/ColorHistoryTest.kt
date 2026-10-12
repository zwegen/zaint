package de.zwegen.zpaint.test.colorpicker

import de.zwegen.zpaint.colorpicker.ColorHistory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.*
import org.junit.Test

class ColorHistoryTest {
    @Test fun keepsFiveRecentColorsAndMovesRepeatedColorsToTheEnd() {
        val history = ColorHistory()
        (1..6).forEach(history::addColor)
        assertEquals(listOf(2, 3, 4, 5, 6), history.colors)
        history.addColor(3)
        assertEquals(listOf(2, 4, 5, 6, 3), history.colors)
    }

    @Test fun newColorsGraduallyReplaceImageColorsWithoutPersistingSuggestions() {
        val history = ColorHistory()
        history.requestImageColors()
        history.seedImageColors(listOf(1, 2, 3, 4, 5))
        assertEquals(listOf(1, 2, 3, 4, 5), history.colors)
        assertTrue(history.storedColors.isEmpty())
        history.addColor(6)
        assertEquals(listOf(2, 3, 4, 5, 6), history.colors)
        assertEquals(listOf(6), history.storedColors)
        (7..10).forEach(history::addColor)
        assertEquals(listOf(6, 7, 8, 9, 10), history.colors)
    }

    @Test fun reopeningDoesNotSeedAgainButANewImageCan() {
        val history = ColorHistory()
        history.requestImageColors()
        history.seedImageColors(listOf(1, 2, 3, 4, 5))
        history.addColor(6)
        history.seedImageColors(listOf(11, 12, 13, 14, 15))
        assertFalse(history.needsImageColors)
        assertEquals(listOf(2, 3, 4, 5, 6), history.colors)
        history.requestImageColors()
        assertTrue(history.needsImageColors)
        history.seedImageColors(listOf(11, 12, 13, 14, 15))
        assertEquals(listOf(11, 12, 13, 14, 15), history.colors)
    }

    @Test fun selectingASuggestionDoesNotDuplicateIt() {
        val history = ColorHistory()
        history.requestImageColors()
        history.seedImageColors(listOf(1, 2, 3, 4, 5))
        history.addColor(3)
        assertEquals(listOf(1, 2, 4, 5, 3), history.colors)
        assertEquals(listOf(3), history.storedColors)
    }

    @Test fun blankImageKeepsNormalHistoryAndCleanCanvasCancelsImageMode() {
        val history = ColorHistory()
        history.addColor(9)
        history.requestImageColors()
        history.seedImageColors(emptyList())
        assertEquals(listOf(9), history.colors)
        assertFalse(history.needsImageColors)
        history.requestImageColors()
        history.seedImageColors(listOf(1, 2))
        history.clearImageColors()
        assertEquals(listOf(9), history.colors)
        assertFalse(history.needsImageColors)
    }

    @Test fun javaSerializationKeepsOnlyUsedColors() {
        val history = ColorHistory()
        history.addColor(9)
        history.requestImageColors()
        history.seedImageColors(listOf(1, 2, 3, 4, 5))
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(history) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as ColorHistory
        }
        assertEquals(listOf(9), restored.colors)
        assertFalse(restored.needsImageColors)
    }
    @Test fun repeatedColorDoesNotEvictOthersOrChangeTheirRelativeOrder() {
        val history = ColorHistory()
        val colors = listOf(0xff111111.toInt(), 0xff5090c0.toInt(), 0xff222222.toInt(),
            0xff333333.toInt(), 0xff444444.toInt())
        colors.forEach(history::addColor)
        history.addColor(colors[1])
        assertEquals(listOf(colors[0], colors[2], colors[3], colors[4], colors[1]), history.colors)
    }

    @Test fun reusingColorRemovesAllLegacyDuplicatesWithoutEvictingOtherColors() {
        val history = ColorHistory()
        val repeated = 0xff5090c0.toInt()
        val first = 0xff111111.toInt()
        val second = 0xff222222.toInt()
        val field = ColorHistory::class.java.getDeclaredField("entries").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val entries = field.get(history) as ArrayList<Int>
        entries.addAll(listOf(first, repeated, second, repeated, repeated))
        history.addColor(repeated)
        assertEquals(listOf(first, second, repeated), history.colors)
        assertEquals(listOf(first, second, repeated), history.storedColors)
    }

}
