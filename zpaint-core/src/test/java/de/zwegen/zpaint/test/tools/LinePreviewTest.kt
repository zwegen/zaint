package de.zwegen.zpaint.test.tools

import android.graphics.PointF
import de.zwegen.zpaint.tools.implementation.LinePreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinePreviewTest {
    @Test
    fun `is visible only with a start and current point`() {
        assertFalse(LinePreview(null, null).isVisible())
        assertFalse(LinePreview(PointF(1f, 2f), null).isVisible())
        assertFalse(LinePreview(null, PointF(3f, 4f)).isVisible())
        assertTrue(LinePreview(PointF(1f, 2f), PointF(3f, 4f)).isVisible())
    }
}
