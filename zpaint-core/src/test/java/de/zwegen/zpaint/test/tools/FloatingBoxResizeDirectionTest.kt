package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxResizeDirection
import de.zwegen.zpaint.tools.implementation.ResizeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBoxResizeDirectionTest {
    private val direction = FloatingBoxResizeDirection()

    @Test
    fun `selects each edge and no resize action for the box center`() {
        assertEquals(ResizeAction.LEFT, resolve(1f, 10f))
        assertEquals(ResizeAction.RIGHT, resolve(19f, 10f))
        assertEquals(ResizeAction.TOP, resolve(10f, 6f))
        assertEquals(ResizeAction.BOTTOM, resolve(10f, 14f))
        assertEquals(ResizeAction.NONE, resolve(10f, 10f))
    }

    @Test
    fun `combines horizontal and vertical frame hits into corners`() {
        assertEquals(ResizeAction.TOPLEFT, resolve(1f, 6f))
        assertEquals(ResizeAction.TOPRIGHT, resolve(19f, 6f))
        assertEquals(ResizeAction.BOTTOMLEFT, resolve(1f, 14f))
        assertEquals(ResizeAction.BOTTOMRIGHT, resolve(19f, 14f))
    }

    private fun resolve(pointX: Float, pointY: Float): ResizeAction =
        direction.resolve(pointX, pointY, 10f, 10f, 20f, 10f, 2f)
}
