package de.zwegen.zpaint.test.tools.implementation

import android.graphics.Rect
import de.zwegen.zpaint.ui.Perspective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BorderPerspectivePreviewTest {
    @Test fun tallImageAndAllAddedEdgesFitOnScreen() = checkFit(2000, 8000, 102)
    @Test fun wideImageAndAllAddedEdgesFitOnScreen() = checkFit(8000, 2000, 102)
    @Test fun smallImageAndAllAddedEdgesFitOnScreen() = checkFit(100, 100, 102)
    @Test fun cropPreviewKeepsOriginalImageInView() = checkFit(2000, 8000, 0)

    @Test
    fun notYetLaidOutSurfaceHasSafeScale() {
        val perspective = Perspective(100, 100)
        perspective.resetScaleAndTranslationWithPadding(102)
        assertEquals(1f, perspective.scale, 0f)
    }

    private fun checkFit(width: Int, height: Int, padding: Int) {
        val perspective = Perspective(width, height)
        perspective.setSurfaceFrame(Rect(0, 0, 1080, 1600))
        perspective.resetScaleAndTranslationWithPadding(padding)
        assertTrue((width + padding) * perspective.scale <= 1072.01f)
        assertTrue((height + padding) * perspective.scale <= 1592.01f)
        assertTrue(perspective.scale > 0f)
    }
}
