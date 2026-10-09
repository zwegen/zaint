package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxSizeLimits
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingBoxSizeLimitsTest {
    private val limits = FloatingBoxSizeLimits(3f)

    @Test
    fun `detects dimensions below the smallest allowed box`() {
        assertTrue(limits.isBelowMinimum(2.9f))
        assertFalse(limits.isBelowMinimum(3f))
    }

    @Test
    fun `restores a box only when the active resolution limit is exceeded`() {
        assertTrue(limits.shouldRestoreForResolution(10f, 11f, true, 100f))
        assertFalse(limits.shouldRestoreForResolution(10f, 11f, false, 100f))
        assertFalse(limits.shouldRestoreForResolution(10f, 11f, true, 0f))
    }
}
