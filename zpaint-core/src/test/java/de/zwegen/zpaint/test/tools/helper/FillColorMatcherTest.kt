package de.zwegen.zpaint.test.tools.helper

import de.zwegen.zpaint.tools.helper.FillColorMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillColorMatcherTest {
    @Test
    fun `accepts an exactly matching color without tolerance`() {
        val matcher = FillColorMatcher(0xFF204060.toInt(), 0f)

        assertTrue(matcher.matches(0xFF204060.toInt()))
        assertFalse(matcher.matches(0xFF204061.toInt()))
    }

    @Test
    fun `includes alpha in tolerant color matching`() {
        val matcher = FillColorMatcher(0xFF204060.toInt(), 10f)

        assertTrue(matcher.matches(0xFF204063.toInt()))
        assertFalse(matcher.matches(0xF0204060.toInt()))
    }
}
