package de.zwegen.zpaint.test.command.bitmap

import de.zwegen.zpaint.command.bitmap.DocumentSize
import de.zwegen.zpaint.command.bitmap.LayerRotationBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerRotationBoundsTest {
    @Test
    fun `quarter turn keeps enough canvas for rotated and unchanged layers`() {
        assertEquals(DocumentSize(1600, 1600), LayerRotationBounds.expandedSize(1600, 900, 90f))
    }

    @Test
    fun `arbitrary turn expands canvas enough to keep all pixels`() {
        assertEquals(DocumentSize(1768, 1768), LayerRotationBounds.expandedSize(1600, 900, 45f))
    }
}
