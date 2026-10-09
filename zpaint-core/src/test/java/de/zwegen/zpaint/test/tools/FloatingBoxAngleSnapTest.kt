package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxAngleSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingBoxAngleSnapTest {
    private val snap = FloatingBoxAngleSnap()

    @Test
    fun `snaps close rotations to the nearest orthogonal angle`() {
        assertEquals(0f, snap.resolve(3.9f, null)!!, 0f)
        assertEquals(90f, snap.resolve(86.1f, null)!!, 0f)
        assertEquals(-90f, snap.resolve(-93.9f, null)!!, 0f)
    }

    @Test
    fun `keeps a snapped angle until it leaves the release range`() {
        assertEquals(0f, snap.resolve(7.9f, 0f)!!, 0f)
        assertNull(snap.resolve(8.1f, 0f))
    }

    @Test
    fun `releases based on the unsnapped angle`() {
        var rawRotation = 0f
        var lockedAngle: Float? = null
        listOf(2f, 4f, 6f, 8.1f).forEach { delta ->
            rawRotation += delta
            lockedAngle = snap.resolve(rawRotation, lockedAngle)
        }

        assertNull(lockedAngle)
        assertEquals(20.1f, rawRotation, 0.001f)
    }
}
