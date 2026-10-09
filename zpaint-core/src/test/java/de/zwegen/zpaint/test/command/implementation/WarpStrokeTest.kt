package de.zwegen.zpaint.test.command.implementation

import de.zwegen.zpaint.command.implementation.WarpPoint
import de.zwegen.zpaint.command.implementation.WarpStrokeMath
import org.junit.Assert.assertEquals
import org.junit.Test

class WarpStrokeTest {
    @Test
    fun pullsTheSampleBackToTheStartOfTheDrag() {
        val source = WarpStrokeMath.inverseWarp(
            18f,
            8f,
            listOf(WarpPoint(10f, 8f), WarpPoint(18f, 8f)),
            10f,
            1f
        )

        assertEquals(10f, source.x)
        assertEquals(8f, source.y)
    }
}
