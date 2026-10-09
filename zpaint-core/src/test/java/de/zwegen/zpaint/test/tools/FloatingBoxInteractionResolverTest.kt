package de.zwegen.zpaint.test.tools

import de.zwegen.zpaint.tools.implementation.FloatingBoxAction
import de.zwegen.zpaint.tools.implementation.FloatingBoxInteractionResolver
import de.zwegen.zpaint.tools.implementation.ResizeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBoxInteractionResolverTest {
    private val resolver = FloatingBoxInteractionResolver()

    @Test
    fun `chooses move resize and rotation in their existing priority`() {
        assertEquals(FloatingBoxAction.MOVE, resolve(10f, 10f).action)
        assertEquals(ResizeAction.LEFT, resolve(1f, 10f).resizeAction)
        assertEquals(FloatingBoxAction.RESIZE, resolve(1f, 10f).action)
        assertEquals(FloatingBoxAction.ROTATE, resolve(-2f, 3f).action)
    }

    @Test
    fun `unrotates the pointer before choosing a resize edge`() {
        val interaction = resolver.resolve(
            10f, 1f, 10f, 10f, 20f, 10f, 90f, 2f, true, 4f
        )
        assertEquals(FloatingBoxAction.RESIZE, interaction.action)
        assertEquals(ResizeAction.LEFT, interaction.resizeAction)
    }

    private fun resolve(pointX: Float, pointY: Float) = resolver.resolve(
        pointX, pointY, 10f, 10f, 20f, 10f, 0f, 2f, true, 4f
    )
}
