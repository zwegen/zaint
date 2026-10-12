package de.zwegen.zpaint.test.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.zwegen.zpaint.tools.implementation.BorderPreviewRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BorderPreviewRendererTest {
    private val renderer = BorderPreviewRenderer()

    @Test
    fun transparentAdditionShowsOpaqueNeutralCheckerboard() {
        val surface = surface(Color.WHITE)
        renderer.draw(surface.second, 100, 100, 40, Color.TRANSPARENT, 1f)
        val light = surface.first.getPixel(28, 92)
        val dark = surface.first.getPixel(52, 92)
        assertEquals(255, Color.alpha(light))
        assertEquals(Color.red(light), Color.blue(light))
        assertEquals(Color.red(dark), Color.blue(dark))
        assertNotEquals(light, dark)
        val checkerColors = setOf(Color.WHITE, Color.rgb(203, 203, 203))
        assertTrue(light in checkerColors && dark in checkerColors)
        assertEquals(Color.WHITE, surface.first.getPixel(110, 110))
        assertEquals(Color.WHITE, surface.first.getPixel(10, 92))
    }

    @Test
    fun opaqueAdditionExactlyPreservesSelectedColorWithoutCheckerOrOutlines() {
        for (color in listOf(Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.rgb(37, 96, 173))) {
            val surface = surface(Color.WHITE)
            renderer.draw(surface.second, 100, 100, 40, color, 1f)
            for ((x, y) in listOf(28 to 92, 52 to 92, 20 to 92, 59 to 92, 160 to 92, 199 to 92)) {
                assertEquals(color, surface.first.getPixel(x, y))
            }
            assertEquals(Color.WHITE, surface.first.getPixel(60, 92))
            assertEquals(Color.WHITE, surface.first.getPixel(159, 92))
            assertEquals(Color.WHITE, surface.first.getPixel(110, 110))
            assertEquals(Color.WHITE, surface.first.getPixel(19, 92))
            assertEquals(Color.WHITE, surface.first.getPixel(200, 92))
        }
    }

    @Test
    fun partiallyTransparentColorShowsCheckerUnderneath() {
        val surface = surface(Color.WHITE)
        renderer.draw(surface.second, 100, 100, 40, Color.argb(128, 255, 0, 0), 1f)
        assertNotEquals(surface.first.getPixel(28, 92), surface.first.getPixel(52, 92))
        assertEquals(255, Color.alpha(surface.first.getPixel(28, 92)))
    }

    @Test
    fun cropMarksOnlyRemovedPixelsInRedAndPreservesImageDetail() {
        val surface = surface(Color.WHITE)
        renderer.draw(surface.second, 100, 100, -30, Color.BLACK, 1f)
        val first = surface.first.getPixel(80, 75)
        val second = surface.first.getPixel(104, 75)
        assertTrue(Color.red(first) > Color.blue(first))
        assertTrue(Color.blue(first) > 190)
        assertNotEquals(first, second)
        assertEquals(Color.WHITE, surface.first.getPixel(110, 110))
        assertEquals(Color.WHITE, surface.first.getPixel(40, 75))
    }

    @Test
    fun cropHasNoOutlineOnRetainedOrOutsidePixels() {
        val surface = surface(Color.WHITE)
        renderer.draw(surface.second, 100, 100, -30, Color.RED, 1f)
        for ((x, y) in listOf(90 to 110, 129 to 110, 110 to 90, 110 to 129, 59 to 110, 160 to 110)) {
            assertEquals(Color.WHITE, surface.first.getPixel(x, y))
        }
    }

    @Test
    fun cropDoesNotCompletelyHideUnderlyingImage() {
        val surface = surface(Color.BLACK)
        renderer.draw(surface.second, 100, 100, -30, Color.WHITE, 1f)
        val preview = surface.first.getPixel(80, 75)
        assertTrue(Color.red(preview) in 1..100)
        assertEquals(Color.BLACK, surface.first.getPixel(110, 110))
    }

    @Test
    fun zeroAndInvalidCropDoNotDrawAnything() {
        for (amount in listOf(0, -50, -60)) {
            val surface = surface(Color.GREEN)
            renderer.draw(surface.second, 100, 100, amount, Color.BLACK, 1f)
            assertTrue(surface.first.sameAs(Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.GREEN)
            }))
        }
    }

    @Test
    fun drawingRestoresCanvasClipAndSaveCount() {
        val surface = surface(Color.WHITE)
        val before = surface.second.saveCount
        renderer.draw(surface.second, 100, 100, 40, Color.TRANSPARENT, 1f)
        assertEquals(before, surface.second.saveCount)
        surface.second.drawColor(Color.GREEN)
        assertEquals(Color.GREEN, surface.first.getPixel(110, 110))
        assertEquals(Color.GREEN, surface.first.getPixel(10, 10))
    }

    @Test
    fun reusingRendererDoesNotLeakCropAlphaIntoAddition() {
        val crop = surface(Color.WHITE)
        renderer.draw(crop.second, 100, 100, -30, Color.BLACK, 1f)
        val addition = surface(Color.TRANSPARENT)
        renderer.draw(addition.second, 100, 100, 40, Color.TRANSPARENT, 1f)
        assertEquals(255, Color.alpha(addition.first.getPixel(28, 92)))
    }

    @Test
    fun checkerSizeStaysStableWhenZoomChanges() {
        val one = zoomedSurface(1f)
        val two = zoomedSurface(2f)
        renderer.draw(one.second, 40, 40, 40, Color.TRANSPARENT, 1f)
        renderer.draw(two.second, 40, 40, 40, Color.TRANSPARENT, 2f)
        assertEquals(one.first.getPixel(53, 110), two.first.getPixel(53, 110))
    }

    private fun surface(color: Int): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return bitmap to Canvas(bitmap).apply { translate(60f, 60f) }
    }

    private fun zoomedSurface(scale: Float): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return bitmap to Canvas(bitmap).apply { translate(80f, 80f); scale(scale, scale) }
    }
}
