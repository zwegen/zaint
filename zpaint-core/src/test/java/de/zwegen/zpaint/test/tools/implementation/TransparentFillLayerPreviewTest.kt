package de.zwegen.zpaint.test.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import de.zwegen.zpaint.tools.implementation.ColorFillPreview
import de.zwegen.zpaint.tools.implementation.ImageFillRegionFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransparentFillLayerPreviewTest {
    @Test
    fun transparentFillRevealsLowerLayerBeforeCommitWithoutChangingSource() {
        val layer = splitLayer()
        val preview = preview(layer)
        val result = bitmap(Color.BLUE)

        assertTrue(preview.drawLayerPreview(Canvas(result), layer, Paint()))

        assertEquals(Color.BLUE, result.getPixel(0, 2))
        assertEquals(Color.BLACK, result.getPixel(2, 2))
        assertEquals(Color.GREEN, result.getPixel(4, 2))
        assertEquals(Color.GREEN, layer.getPixel(0, 2))
        preview.release()
    }

    @Test
    fun transparentFillPreservesCheckerboardWhenThereIsNoLowerLayerContent() {
        val layer = splitLayer()
        val preview = preview(layer)
        val result = bitmap(Color.BLUE).apply { setPixel(0, 2, Color.RED) }

        assertTrue(preview.drawLayerPreview(Canvas(result), layer, Paint()))

        assertEquals(Color.RED, result.getPixel(0, 2))
        assertEquals(Color.BLUE, result.getPixel(1, 2))
        preview.release()
    }

    @Test
    fun layerOpacityIsAppliedOnceWithoutErasingLowerLayers() {
        val layer = splitLayer()
        val preview = preview(layer)
        val result = bitmap(Color.TRANSPARENT)

        assertTrue(preview.drawLayerPreview(Canvas(result), layer, Paint().apply { alpha = 128 }))

        assertEquals(0, Color.alpha(result.getPixel(0, 2)))
        assertEquals(128, Color.alpha(result.getPixel(4, 2)))
        preview.release()
    }

    @Test
    fun multipleRegionsAndRemovingARegionUpdateTheLayerPreview() {
        val layer = splitLayer()
        val preview = preview(layer)
        preview.addRegion(ImageFillRegionFinder.find(layer, 4, 2, 0f)!!)
        val result = bitmap(Color.BLUE)
        assertTrue(preview.drawLayerPreview(Canvas(result), layer, Paint()))
        assertEquals(Color.BLUE, result.getPixel(0, 2))
        assertEquals(Color.BLUE, result.getPixel(4, 2))
        assertEquals(Color.BLACK, result.getPixel(2, 2))

        assertTrue(preview.removeRegionAt(PointF(4f, 2f)))
        result.eraseColor(Color.BLUE)
        assertTrue(preview.drawLayerPreview(Canvas(result), layer, Paint()))
        assertEquals(Color.BLUE, result.getPixel(0, 2))
        assertEquals(Color.GREEN, result.getPixel(4, 2))
        preview.release()
    }

    @Test
    fun nonTransparentFillKeepsTheExistingDrawingPath() {
        val layer = splitLayer()
        val region = ImageFillRegionFinder.find(layer, 0, 2, 0f)!!
        val preview = ColorFillPreview(region, intArrayOf(Color.RED), FillGradientDirection.TOP_BOTTOM)
        val result = bitmap(Color.BLUE)

        assertFalse(preview.drawLayerPreview(Canvas(result), layer, Paint()))

        assertEquals(Color.BLUE, result.getPixel(0, 2))
        assertEquals(Color.BLUE, result.getPixel(4, 2))
        preview.release()
    }

    private fun bitmap(color: Int) = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888).apply {
        eraseColor(color)
    }

    private fun splitLayer() = bitmap(Color.GREEN).apply {
        for (y in 0 until height) setPixel(2, y, Color.BLACK)
    }

    private fun preview(layer: Bitmap): ColorFillPreview {
        val region = ImageFillRegionFinder.find(layer, 0, 2, 0f)!!
        return ColorFillPreview(region, intArrayOf(Color.TRANSPARENT), FillGradientDirection.TOP_BOTTOM,
            layer, antialiasing = false)
    }
}
