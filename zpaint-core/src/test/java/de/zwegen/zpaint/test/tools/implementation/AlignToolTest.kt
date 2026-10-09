package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.AlignTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlignToolTest {
    @Test
    fun horizontalReferenceLine_isCorrectedToHorizontal() {
        val correction = AlignTool.correctionAngle(0f, 0f, 100f, 10f)

        assertEquals(-5.710593f, correction ?: Float.NaN, 0.0001f)
    }

    @Test
    fun verticalReferenceLine_isCorrectedToVertical() {
        val correction = AlignTool.correctionAngle(0f, 0f, 10f, 100f)

        assertEquals(5.710593f, correction ?: Float.NaN, 0.0001f)
    }

    @Test
    fun tooShortReferenceLine_isIgnored() {
        assertNull(AlignTool.correctionAngle(0f, 0f, 2f, 2f))
    }
}
