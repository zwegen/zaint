package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.TextCurveRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCurveRendererTest {
    @Test
    fun straightTextHasNoSagitta() {
        assertEquals(0f, TextCurveRenderer.sagitta(300f, 0f), 0f)
    }

    @Test
    fun curvatureIsSymmetricAboveAndBelow() {
        assertEquals(
            TextCurveRenderer.sagitta(300f, 120f),
            TextCurveRenderer.sagitta(300f, -120f),
            0.001f
        )
    }

    @Test
    fun strongerCurveHasLargerSagitta() {
        assertTrue(
            TextCurveRenderer.sagitta(300f, 180f) > TextCurveRenderer.sagitta(300f, 60f)
        )
    }
}
