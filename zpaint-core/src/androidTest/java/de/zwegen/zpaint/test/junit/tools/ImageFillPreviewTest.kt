package de.zwegen.zpaint.test.junit.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.zwegen.zpaint.tools.implementation.ImageFillPreview
import de.zwegen.zpaint.tools.implementation.ImageFillRegionFinder
import de.zwegen.zpaint.tools.implementation.ImageFillRegion
import de.zwegen.zpaint.tools.implementation.ColorFillPreview
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageFillPreviewTest {
    @Test
    fun imagePreviewOnlyCoversTheTappedConnectedRegion() {
        val target = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 0 until height) setPixel(2, y, Color.BLACK)
        }
        val region = ImageFillRegionFinder.find(target, 0, 0, 0f)!!
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, region, PointF(1f, 2f))
        val result = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))

        assertTrue(region.pixels[0])
        assertFalse(region.pixels[2])
        assertEquals(Color.red(Color.RED), Color.red(result.getPixel(0, 0)))
        assertEquals(255, Color.alpha(result.getPixel(0, 0)))
        assertTrue(Color.alpha(result.getPixel(1, 2)) in 1 until 255)
        assertEquals(Color.TRANSPARENT, result.getPixel(2, 0))
        assertEquals(Color.TRANSPARENT, result.getPixel(4, 3))
        preview.release()
    }

    @Test
    fun smallEnclosedIslandIsIncludedInTheFinalImagePreview() {
        val target = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setPixel(3, 3, Color.BLACK)
        }

        val region = ImageFillRegionFinder.find(target, 0, 0, 0f)!!
        val source = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, region, PointF(3f, 3f))
        val result = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(result))

        assertTrue(Color.alpha(result.getPixel(3, 3)) > 0)
        preview.release()
    }

    @Test
    fun largeEnclosedIslandIsNotIncludedInTheFinalImagePreview() {
        val target = Bitmap.createBitmap(13, 13, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 1..11) for (x in 1..11) setPixel(x, y, Color.BLACK)
        }

        val region = ImageFillRegionFinder.find(target, 0, 0, 0f)!!
        val source = Bitmap.createBitmap(13, 13, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, region, PointF(6f, 6f))
        val result = Bitmap.createBitmap(13, 13, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(result))

        assertEquals(Color.TRANSPARENT, result.getPixel(6, 6))
        preview.release()
    }

    @Test
    fun colorPreviewIncludesSmallEnclosedIsland() {
        val target = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setPixel(3, 3, Color.BLACK)
        }
        val region = ImageFillRegionFinder.find(target, 0, 0, 0f)!!
        val preview = ColorFillPreview(region, intArrayOf(Color.RED), FillGradientDirection.TOP_BOTTOM)
        val result = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))

        assertEquals(Color.RED, result.getPixel(3, 3))
        assertEquals(Color.RED, result.getPixel(0, 0))
        preview.release()
    }

    @Test
    fun colorPreviewUsesTheSameSoftEdgeAsImageFill() {
        val target = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            for (y in 5 until 95) for (x in 5 until 95) setPixel(x, y, Color.WHITE)
        }
        val region = ImageFillRegionFinder.find(target, 50, 50, 0f)!!
        val preview = ColorFillPreview(region, intArrayOf(Color.RED), FillGradientDirection.TOP_BOTTOM)
        val result = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))

        assertEquals(Color.TRANSPARENT, result.getPixel(4, 50))
        assertTrue(Color.alpha(result.getPixel(5, 50)) in 1 until 255)
        assertTrue(Color.alpha(result.getPixel(6, 50)) in Color.alpha(result.getPixel(5, 50)) until 255)
        assertEquals(255, Color.alpha(result.getPixel(7, 50)))
        assertEquals(255, Color.alpha(result.getPixel(8, 50)))
        assertEquals(255, Color.alpha(result.getPixel(9, 50)))
        preview.release()
    }

    @Test
    fun transparentColorPreviewShowsCheckerboardOnlyInsideSelectedArea() {
        val target = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 0 until height) setPixel(2, y, Color.BLACK)
        }
        val region = ImageFillRegionFinder.find(target, 0, 0, 0f)!!
        val checker = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.RED)
            setPixel(1, 0, Color.BLUE)
            setPixel(0, 1, Color.BLUE)
            setPixel(1, 1, Color.RED)
        }
        val preview = ColorFillPreview(region, intArrayOf(Color.TRANSPARENT), FillGradientDirection.TOP_BOTTOM)
        val result = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result), BitmapShader(checker, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))

        assertEquals(Color.RED, result.getPixel(0, 0))
        assertEquals(Color.TRANSPARENT, result.getPixel(4, 2))
        preview.release()
    }

    @Test
    fun imagePreviewRechecksIslandsAfterAddingAndRemovingRegions() {
        val first = region(7, Rect(2, 2, 5, 5), listOf(
            2 to 2, 3 to 2, 4 to 2, 2 to 3, 2 to 4, 3 to 4, 4 to 4
        ))
        val second = region(7, Rect(4, 3, 5, 4), listOf(4 to 3))
        val source = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, first, PointF(3f, 3f))

        preview.addRegion(second)
        val combined = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(combined))
        assertTrue(Color.alpha(combined.getPixel(3, 3)) > 0)

        assertTrue(preview.removeRegionAt(PointF(4f, 3f)))
        val removed = Bitmap.createBitmap(7, 7, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(removed))
        assertEquals(Color.TRANSPARENT, removed.getPixel(3, 3))
        preview.release()
    }

    @Test
    fun whiteFillBesideOpaqueWhiteHasNoTransparentSeam() {
        val original = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (y in 0 until height) for (x in 10 until width) setPixel(x, y, Color.WHITE)
        }
        val region = ImageFillRegionFinder.find(original, 5, 6, 0f)!!
        val preview = ColorFillPreview(region, intArrayOf(Color.WHITE), FillGradientDirection.TOP_BOTTOM, original)
        val result = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888)
        Canvas(result).apply { drawBitmap(original, 0f, 0f, null); preview.draw(this) }
        for (x in 7..10) assertEquals(Color.WHITE, result.getPixel(x, 6))
        preview.release()
    }

    @Test
    fun imageFillDoesNotBlendOriginalNeighbourIntoItsEdge() {
        val original = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (y in 0 until height) for (x in 10 until width) setPixel(x, y, Color.WHITE)
        }
        val region = ImageFillRegionFinder.find(original, 5, 6, 0f)!!
        val source = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val preview = ImageFillPreview(source, region, PointF(10f, 6f))
        val result = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(result))
        assertTrue(Color.alpha(result.getPixel(9, 6)) in 1 until 255)
        assertEquals(0, Color.green(result.getPixel(9, 6)))
        assertEquals(255, Color.alpha(result.getPixel(8, 6)))
        assertEquals(Color.TRANSPARENT, result.getPixel(10, 6))
        preview.release()
    }

    private fun region(width: Int, bounds: Rect, points: List<Pair<Int, Int>>): ImageFillRegion {
        return ImageFillRegion(width, BooleanArray(width * width).apply {
            points.forEach { (x, y) -> this[y * width + x] = true }
        }, bounds)
    }

    @Test
    fun imagePreviewAntialiasesOneBoundaryPixel() {
        val target = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            for (y in 5 until 95) for (x in 5 until 95) setPixel(x, y, Color.WHITE)
        }
        val region = ImageFillRegionFinder.find(target, 50, 50, 0f)!!
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, region, PointF(50f, 50f))
        val result = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))

        assertEquals(Color.TRANSPARENT, result.getPixel(4, 50))
        assertTrue(Color.alpha(result.getPixel(5, 50)) in 1 until 255)
        assertEquals(255, Color.alpha(result.getPixel(6, 50)))
        assertEquals(255, Color.alpha(result.getPixel(7, 50)))
        assertEquals(255, Color.alpha(result.getPixel(9, 50)))
        preview.release()
    }

    @Test
    fun imagePreviewToggleSwitchesBetweenSmoothAndHardSelectionEdge() {
        val target = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            for (y in 2..9) for (x in 2..9) setPixel(x, y, Color.WHITE)
        }
        val region = ImageFillRegionFinder.find(target, 6, 6, 0f)!!
        val source = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, region, PointF(6f, 6f))
        val result = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))
        assertTrue(Color.alpha(result.getPixel(2, 6)) in 1 until 255)

        preview.setAntialiasing(false)
        result.eraseColor(Color.TRANSPARENT)
        preview.draw(Canvas(result))
        assertEquals(255, Color.alpha(result.getPixel(2, 6)))
        assertEquals(Color.TRANSPARENT, result.getPixel(1, 6))

        preview.setAntialiasing(true)
        result.eraseColor(Color.TRANSPARENT)
        preview.draw(Canvas(result))
        assertTrue(Color.alpha(result.getPixel(2, 6)) in 1 until 255)
        preview.release()
    }

    @Test
    fun imagePreviewKeepsImageEdgeFullyOpaque() {
        val target = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val region = ImageFillRegionFinder.find(target, 10, 10, 0f)!!
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, region, PointF(10f, 10f))
        val result = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))

        for (point in listOf(0 to 10, 19 to 10, 10 to 0, 10 to 19)) {
            assertEquals(255, Color.alpha(result.getPixel(point.first, point.second)))
        }
        preview.release()
    }

    @Test
    fun colorPreviewKeepsAllCanvasEdgesFullyOpaque() {
        val target = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val region = ImageFillRegionFinder.find(target, 10, 10, 0f)!!
        val preview = ColorFillPreview(region, intArrayOf(Color.RED), FillGradientDirection.TOP_BOTTOM)
        val result = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))

        for (point in listOf(0 to 10, 19 to 10, 10 to 0, 10 to 19)) {
            assertEquals(Color.RED, result.getPixel(point.first, point.second))
        }
        preview.release()
    }

    @Test
    fun imagePreviewCanCoverMultipleTappedRegionsWithOneSharedImage() {
        val target = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 0 until height) setPixel(2, y, Color.BLACK)
        }
        val firstRegion = ImageFillRegionFinder.find(target, 0, 0, 0f)!!
        val secondRegion = ImageFillRegionFinder.find(target, 4, 0, 0f)!!
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, firstRegion, PointF(1f, 2f))
        preview.addRegion(secondRegion)
        val result = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888)

        preview.draw(Canvas(result))

        assertTrue(Color.alpha(result.getPixel(0, 0)) > 0)
        assertTrue(Color.alpha(result.getPixel(1, 3)) > 0)
        assertEquals(Color.TRANSPARENT, result.getPixel(2, 0))
        assertTrue(Color.alpha(result.getPixel(3, 0)) > 0)
        assertTrue(Color.alpha(result.getPixel(4, 3)) > 0)
        preview.release()
    }

    @Test
    fun imagePreviewRemovesOnlyTheTappedSelectedRegion() {
        val target = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 0 until height) setPixel(2, y, Color.BLACK)
        }
        val firstRegion = ImageFillRegionFinder.find(target, 0, 0, 0f)!!
        val secondRegion = ImageFillRegionFinder.find(target, 4, 0, 0f)!!
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val preview = ImageFillPreview(source, firstRegion, PointF(1f, 2f))
        preview.addRegion(secondRegion)

        assertTrue(preview.removeRegionAt(PointF(4f, 0f)))
        val result = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888)
        preview.draw(Canvas(result))

        assertTrue(Color.alpha(result.getPixel(0, 0)) > 0)
        assertEquals(Color.TRANSPARENT, result.getPixel(3, 0))
        assertFalse(preview.isEmpty)
        preview.release()
    }
}
