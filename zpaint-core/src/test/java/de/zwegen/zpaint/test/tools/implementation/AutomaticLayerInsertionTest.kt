package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.AutomaticLayerInsertion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticLayerInsertionTest {
    @Test
    fun `preserves the first layer without calculating overlap`() {
        assertTrue(
            AutomaticLayerInsertion.shouldCreateNewLayer(layerCount = 1) {
                error("A single-layer document must not need an overlap calculation")
            }
        )
    }

    @Test
    fun `uses a new layer for overlap after the first layer`() {
        assertTrue(
            AutomaticLayerInsertion.shouldCreateNewLayer(layerCount = 2) { true }
        )
    }

    @Test
    fun `keeps non-overlapping content on the active layer after the first layer`() {
        assertFalse(
            AutomaticLayerInsertion.shouldCreateNewLayer(layerCount = 2) { false }
        )
    }
}
