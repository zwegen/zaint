package de.zwegen.zpaint.test.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.tools.implementation.BorderPreviewRenderer
import de.zwegen.zpaint.tools.implementation.BorderTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BorderLayerPreviewTest {
    @Test
    fun borderPreviewDrawsAboveAllLayersNotJustTheActiveLayer() {
        checkComposite(activeVisible = true, currentIndex = 1)
    }

    @Test
    fun borderPreviewDoesNotDependOnActiveLayerVisibility() {
        checkComposite(activeVisible = false, currentIndex = 1)
    }

    @Test
    fun borderPreviewDoesNotDependOnCurrentLayerIndex() {
        checkComposite(activeVisible = false, currentIndex = null)
    }

    @Test
    fun previewAlsoDrawsToDrawingBoardCanvas() {
        val model = model(activeVisible = true)
        val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { translate(60f, 60f) }
        val tool = previewTool()
        model.drawLayersOntoCanvasCorrectOrder(null, 1, canvas, tool)
        Mockito.verify(tool, Mockito.times(1)).draw(canvas)
        val pixel = bitmap.getPixel(80, 75)
        assertTrue(Color.red(pixel) > Color.blue(pixel))
    }

    private fun checkComposite(activeVisible: Boolean, currentIndex: Int?) {
        val model = model(activeVisible)
        val originals = model.layers.map { it.bitmap.copy(Bitmap.Config.ARGB_8888, false) }
        val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { translate(60f, 60f) }
        val tool = previewTool()
        model.drawLayersOntoCanvasCorrectOrder(canvas, currentIndex, null, tool)
        val cropped = bitmap.getPixel(80, 75)
        assertTrue(Color.red(cropped) > Color.blue(cropped))
        assertEquals(Color.WHITE, bitmap.getPixel(110, 110))
        Mockito.verify(tool, Mockito.times(1)).draw(canvas)
        model.layers.forEachIndexed { index, layer -> assertTrue(layer.bitmap.sameAs(originals[index])) }
    }

    private fun model(activeVisible: Boolean): ZaintLayerModel {
        val top = ZaintLayer(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })
        val active = ZaintLayer(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) })
        active.isVisible = activeVisible
        return ZaintLayerModel().apply {
            width = 100
            height = 100
            addLayerAt(0, top)
            addLayerAt(1, active)
            currentLayer = active
        }
    }

    private fun previewTool(): BorderTool {
        val tool = Mockito.mock(BorderTool::class.java)
        val renderer = BorderPreviewRenderer()
        Mockito.doAnswer { invocation ->
            renderer.draw(invocation.getArgument(0), 100, 100, -30, Color.BLACK, 1f)
            null
        }.`when`(tool).draw(any())
        return tool
    }
}
