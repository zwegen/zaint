package de.zwegen.zpaint.test.ui

import de.zwegen.zpaint.ui.tools.snapToNeutralZero
import org.junit.Assert.assertEquals
import org.junit.Test

class ZaintTopNumberSliderTest {
    @Test
    fun snapsTwoStepsAroundTheCenteredZeroToZero() {
        assertEquals(0, snapToNeutralZero(-1, -100, 100))
        assertEquals(0, snapToNeutralZero(0, -100, 100))
        assertEquals(0, snapToNeutralZero(1, -100, 100))
        assertEquals(0, snapToNeutralZero(-2, -100, 100))
        assertEquals(0, snapToNeutralZero(2, -100, 100))
    }

    @Test
    fun keepsValuesOutsideTheZeroSnapRange() {
        assertEquals(-3, snapToNeutralZero(-3, -100, 100))
        assertEquals(3, snapToNeutralZero(3, -100, 100))
    }

    @Test
    fun doesNotSnapRangesWithoutACenteredZero() {
        assertEquals(0, snapToNeutralZero(0, 0, 100))
        assertEquals(-1, snapToNeutralZero(-1, -50, 100))
    }
}
