package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxResizeDelta
import de.zwegen.zpaint.tools.implementation.ResizeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBoxResizeDeltaTest {
    private val resizeDelta = FloatingBoxResizeDelta()

    @Test
    fun `converts movement into the local coordinates of a rotated box`() {
        val movement = resizeDelta.resolve(0f, 4f, 90f, ResizeAction.RIGHT, 10f, 5f)

        assertEquals(4f, movement.horizontal, 0.001f)
        assertEquals(0f, movement.vertical, 0.001f)
    }

    @Test
    fun `keeps the aspect ratio for diagonal resize actions`() {
        val topLeft = resizeDelta.resolve(4f, 1f, 0f, ResizeAction.TOPLEFT, 10f, 5f)
        val topRight = resizeDelta.resolve(4f, 1f, 0f, ResizeAction.TOPRIGHT, 10f, 5f)

        assertEquals(2f, topLeft.vertical, 0.001f)
        assertEquals(-2f, topRight.vertical, 0.001f)
    }

    @Test
    fun `allows independent dimensions when corner aspect locking is disabled`() {
        val movement = resizeDelta.resolve(4f, 1f, 0f, ResizeAction.TOPLEFT, 10f, 5f, false)

        assertEquals(4f, movement.horizontal, 0.001f)
        assertEquals(1f, movement.vertical, 0.001f)
    }
}
