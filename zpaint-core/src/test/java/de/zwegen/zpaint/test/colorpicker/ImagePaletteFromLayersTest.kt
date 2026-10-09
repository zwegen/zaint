package de.zwegen.zpaint.test.colorpicker

import android.graphics.Bitmap
import android.graphics.Color
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.ui.ImagePaletteFromLayers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePaletteFromLayersTest {
    @Test fun samplesVisibleLayersInCorrectOrderAndLeavesSourceBitmapsAlive() {
        val top = bitmap(intArrayOf(Color.RED, Color.TRANSPARENT))
        val bottom = bitmap(intArrayOf(Color.BLUE, Color.BLUE))
        val hidden = bitmap(intArrayOf(Color.GREEN, Color.GREEN))
        try {
            val result = ImagePaletteFromLayers.extract(model(listOf(
                layer(hidden, false), layer(top), layer(bottom))))
            assertEquals(setOf(Color.RED, Color.BLUE), result.toSet())
            assertFalse(top.isRecycled)
            assertFalse(bottom.isRecycled)
        } finally {
            top.recycle()
            bottom.recycle()
            hidden.recycle()
        }
    }

    @Test fun emptyDimensionsAndTransparentPicturesHaveNoSuggestions() {
        val empty = mock<ZaintLayerContracts.Model>()
        assertTrue(ImagePaletteFromLayers.extract(empty).isEmpty())
        val transparent = bitmap(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        try {
            assertTrue(ImagePaletteFromLayers.extract(model(listOf(layer(transparent)))).isEmpty())
        } finally {
            transparent.recycle()
        }
    }

    private fun bitmap(pixels: IntArray) = Bitmap.createBitmap(pixels, 2, 1, Bitmap.Config.ARGB_8888)

    private fun layer(bitmap: Bitmap, visible: Boolean = true): ZaintLayerContracts.ZaintLayer =
        mock<ZaintLayerContracts.ZaintLayer>().also {
            whenever(it.bitmap).thenReturn(bitmap)
            whenever(it.isVisible).thenReturn(visible)
            whenever(it.getValueForOpacityPercentage()).thenReturn(255)
        }

    private fun model(layers: List<ZaintLayerContracts.ZaintLayer>): ZaintLayerContracts.Model =
        mock<ZaintLayerContracts.Model>().also {
            whenever(it.width).thenReturn(2)
            whenever(it.height).thenReturn(1)
            whenever(it.listIterator(0)).thenAnswer { layers.listIterator() }
        }
}
