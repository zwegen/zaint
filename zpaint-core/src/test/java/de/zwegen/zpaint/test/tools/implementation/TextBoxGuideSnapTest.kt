package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.implementation.TextBoxGuideSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextBoxGuideSnapTest {
    private val snap = TextBoxGuideSnap()

    @Test
    fun `centre snaps independently to quarter lines`() {
        val result = snap.resolve(243f, 452f, 243f, 452f, 1000f, 600f, 120f, 80f, 0f)
        assertEquals(250f, result.x, 0f)
        assertEquals(450f, result.y, 0f)
        assertEquals(250f, result.verticalGuide)
        assertEquals(450f, result.horizontalGuide)
    }

    @Test
    fun `outer bounds snap flush to all four edges`() {
        val topLeft = snap.resolve(54f, 43f, 54f, 43f, 1000f, 600f, 100f, 80f, 0f)
        assertEquals(50f, topLeft.x, 0f)
        assertEquals(40f, topLeft.y, 0f)
        assertEquals(0f, topLeft.verticalGuide)
        assertEquals(0f, topLeft.horizontalGuide)
        snap.reset()
        val bottomRight = snap.resolve(946f, 557f, 946f, 557f, 1000f, 600f, 100f, 80f, 0f)
        assertEquals(950f, bottomRight.x, 0f)
        assertEquals(560f, bottomRight.y, 0f)
        assertEquals(1000f, bottomRight.verticalGuide)
        assertEquals(600f, bottomRight.horizontalGuide)
    }

    @Test
    fun `rotated outer bounds determine edge position`() {
        val result = snap.resolve(961f, 300f, 961f, 300f, 1000f, 600f, 100f, 80f, 90f)
        assertEquals(960f, result.x, 0.001f)
        assertEquals(1000f, result.verticalGuide)
    }

    @Test
    fun `pulling away releases a lock`() {
        snap.resolve(500f, 300f, 500f, 300f, 1000f, 600f, 100f, 80f, 0f)
        val released = snap.resolve(500f, 300f, 530f, 300f, 1000f, 600f, 100f, 80f, 0f)
        val away = snap.resolve(541f, 300f, 571f, 300f, 1000f, 600f, 100f, 80f, 0f)
        assertEquals(500f, released.x, 0f)
        assertEquals(541f, away.x, 0f)
        assertNull(away.verticalGuide)
    }
}
