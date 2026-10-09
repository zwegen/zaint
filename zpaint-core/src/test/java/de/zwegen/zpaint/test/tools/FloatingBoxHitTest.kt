package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxHitTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingBoxHitTestTest {
    private val hitTest = FloatingBoxHitTest()

    @Test
    fun `finds a point within an unrotated box`() {
        assertTrue(hitTest.contains(3f, 2f, 0f, 0f, 10f, 6f, 0f))
        assertFalse(hitTest.contains(5f, 0f, 0f, 0f, 10f, 6f, 0f))
    }

    @Test
    fun `uses the box rotation when checking a point`() {
        assertTrue(hitTest.contains(0f, 4f, 0f, 0f, 10f, 4f, 90f))
        assertFalse(hitTest.contains(4f, 0f, 0f, 0f, 10f, 4f, 90f))
    }
}
