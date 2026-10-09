package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.AutoScrollResolver
import de.zwegen.zpaint.tools.implementation.AutoScrollStep
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoScrollResolverTest {
    private val resolver = AutoScrollResolver(tolerance = 20)

    @Test
    fun `does not scroll inside the canvas`() {
        assertEquals(AutoScrollStep(0, 0), resolver.resolve(50f, 50f, 100, 100))
    }

    @Test
    fun `scrolls away from each reached edge`() {
        assertEquals(AutoScrollStep(1, 1), resolver.resolve(10f, 10f, 100, 100))
        assertEquals(AutoScrollStep(-1, -1), resolver.resolve(90f, 90f, 100, 100))
    }
}
