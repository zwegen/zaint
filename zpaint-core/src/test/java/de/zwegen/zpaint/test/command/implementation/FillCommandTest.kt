package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import de.zwegen.zpaint.command.implementation.GradientFillCommand
import de.zwegen.zpaint.command.implementation.ZaintFloodFill
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.tools.helper.FillAlgorithm
import de.zwegen.zpaint.tools.helper.FillAlgorithmFactory
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class FillCommandTest {
    @Mock
    private val fillAlgorithmFactory: FillAlgorithmFactory? = null

    @Mock
    private val fillAlgorithm: FillAlgorithm? = null

    @Mock
    private val clickedPixel: Point? = null

    @Mock
    private val paint: Paint? = null

    @Before
    fun setUp() { `when`(fillAlgorithmFactory?.createFillAlgorithm()).thenReturn(fillAlgorithm) }

    @Test
    fun testSetUp() {
        val command = fillAlgorithmFactory?.let {
            if (clickedPixel != null && paint != null) {
                ZaintFloodFill(it, clickedPixel, paint, 0F)
            }
        }

        Assert.assertNotNull(command)
        Mockito.verifyNoInteractions(fillAlgorithmFactory, clickedPixel, paint)
        setUp()
    }

    @Test
    fun testRun() {
        val clickedPixel = Point(3, 5)
        val paint = Mockito.mock(Paint::class.java)
        `when`(paint.color).thenReturn(Color.BLUE)
        val command = ZaintFloodFill(fillAlgorithmFactory!!, clickedPixel, paint, 0.5f)

        val canvas = Mockito.mock(Canvas::class.java)
        val layerModel = ZaintLayerModel()
        val layer = Mockito.mock(ZaintLayerContracts.ZaintLayer::class.java)
        val bitmap = Mockito.mock(Bitmap::class.java)

        layerModel.currentLayer = layer
        `when`(layer.bitmap).thenReturn(bitmap)
        command.run(canvas, layerModel)

        Mockito.verify(fillAlgorithm)?.setParameters(bitmap, clickedPixel, Color.BLUE, Color.TRANSPARENT, 0.5f)
        Mockito.verify(fillAlgorithm)?.performFilling()
    }

    @Test
    fun threeColorGradientKeepsAllPaletteColors() {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE)

        val command = GradientFillCommand(
            Point(3, 5),
            Mockito.mock(Paint::class.java),
            0f,
            colors,
            FillGradientDirection.TOP_BOTTOM
        )

        Assert.assertArrayEquals(colors, command.colors)
    }
}
