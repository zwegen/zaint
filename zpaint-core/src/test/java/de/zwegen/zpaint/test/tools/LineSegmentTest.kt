package de.zwegen.zpaint.test.tools

import android.graphics.PointF
import de.zwegen.zpaint.tools.implementation.LineSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineSegmentTest {
    @Test
    fun `is complete only with both endpoints`() {
        assertFalse(LineSegment(PointF(), null).isComplete())
        assertTrue(LineSegment(PointF(), PointF()).isComplete())
    }
}
