package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxRotation
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBoxRotationTest {
    private val rotation = FloatingBoxRotation()

    @Test
    fun `turns the box by a quarter turn around its center`() {
        assertEquals(-90f, rotation.afterDrag(0f, 10f, 10f, 10f, 0f, 0f, 0f), 0.001f)
    }

    @Test
    fun `keeps the rotation within the signed half turn range`() {
        assertEquals(55f, rotation.afterDrag(-10f, 0f, 0f, 10f, 0f, 0f, 100f), 0.001f)
    }
}
