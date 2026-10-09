package de.zwegen.zpaint.test.tools

import android.graphics.PointF
import de.zwegen.zpaint.tools.implementation.LineInputState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineInputStateTest {
    @Test
    fun `stores points after pointer distance is applied`() {
        val state = LineInputState()

        assertNotNull(state.setStart(PointF(10f, 10f), 2f, 3f))
        assertNotNull(state.setEnd(PointF(7f, 8f), 2f, 4f))

        assertTrue(state.isStartSet)
        assertTrue(state.isEndSet)
    }

    @Test
    fun `continuing a line moves the end point to the next start`() {
        val state = LineInputState()
        state.setStart(PointF(1f, 2f), 0f, 0f)
        state.setEnd(PointF(5f, 6f), 0f, 0f)
        state.isFinalized = true

        assertNotNull(state.beginConnectedSegment())
        assertNotNull(state.initialCoordinate)
        assertNull(state.startPoint)
        assertNull(state.endPoint)
        assertFalse(state.isStartSet)
        assertFalse(state.isEndSet)
        assertFalse(state.isFinalized)
    }

}
