package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.CanvasCenterGuideSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasCenterGuideSnapTest {
    private val snap = CanvasCenterGuideSnap()

    @Test
    fun `snaps each axis independently at the canvas centre`() {
        val result = snap.resolve(
            positionX = 492f,
            positionY = 340f,
            touchX = 492f,
            touchY = 340f,
            canvasWidth = 1_000f,
            canvasHeight = 600f
        )

        assertEquals(500f, result.x, 0f)
        assertEquals(340f, result.y, 0f)
        assertTrue(result.showVerticalGuide)
        assertFalse(result.showHorizontalGuide)
    }

    @Test
    fun `releases a snapped axis until the drag has moved away`() {
        snap.resolve(500f, 300f, 500f, 300f, 1_000f, 600f)
        val released = snap.resolve(500f, 300f, 530f, 300f, 1_000f, 600f)
        val movedAway = snap.resolve(541f, 300f, 571f, 300f, 1_000f, 600f)

        assertEquals(500f, released.x, 0f)
        assertEquals(541f, movedAway.x, 0f)
        assertFalse(movedAway.showVerticalGuide)
    }
}
