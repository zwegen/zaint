package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.ZeroValueSnap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroValueSnapTest {
    private val snap = ZeroValueSnap(snapDistance = 4f, releaseDistance = 8f)

    @Test
    fun `snaps when the raw curvature is close to straight`() {
        assertTrue(snap.resolve(4f, locked = false))
        assertTrue(snap.resolve(-3.9f, locked = false))
        assertFalse(snap.resolve(4.1f, locked = false))
    }

    @Test
    fun `keeps the straight snap until leaving the release range`() {
        assertTrue(snap.resolve(8f, locked = true))
        assertFalse(snap.resolve(-8.1f, locked = true))
    }
}
