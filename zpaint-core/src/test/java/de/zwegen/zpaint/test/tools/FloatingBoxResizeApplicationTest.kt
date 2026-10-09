package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxResizeApplication
import de.zwegen.zpaint.tools.implementation.FloatingBoxResizeMovement
import de.zwegen.zpaint.tools.implementation.FloatingBoxResizeState
import de.zwegen.zpaint.tools.implementation.ResizeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBoxResizeApplicationTest {
    private val application = FloatingBoxResizeApplication()
    private val state = FloatingBoxResizeState(10f, 10f, 20f, 10f)

    @Test
    fun `moves the center while resizing from a top edge`() {
        val result = application.apply(
            state, FloatingBoxResizeMovement(0f, 4f, 0.0), ResizeAction.TOP, 100f, 100f, true
        )

        assertEquals(10f, result.centerX, 0.001f)
        assertEquals(12f, result.centerY, 0.001f)
        assertEquals(6f, result.height, 0.001f)
    }

    @Test
    fun `applies both dimensions for a corner resize`() {
        val result = application.apply(
            state, FloatingBoxResizeMovement(4f, 2f, 0.0), ResizeAction.BOTTOMRIGHT, 100f, 100f, true
        )

        assertEquals(12f, result.centerX, 0.001f)
        assertEquals(11f, result.centerY, 0.001f)
        assertEquals(24f, result.width, 0.001f)
        assertEquals(12f, result.height, 0.001f)
    }

    @Test
    fun `limits a dimension without moving the center`() {
        val result = application.apply(
            state, FloatingBoxResizeMovement(4f, 0f, 0.0), ResizeAction.RIGHT, 21f, 100f, true
        )

        assertEquals(10f, result.centerX, 0.001f)
        assertEquals(21f, result.width, 0.001f)
    }
}
