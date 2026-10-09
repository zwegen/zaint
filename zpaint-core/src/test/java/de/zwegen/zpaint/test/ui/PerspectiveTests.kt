package de.zwegen.zpaint.test.ui

import android.graphics.Canvas
import android.graphics.Rect
import org.junit.runner.RunWith
import org.mockito.Mock
import android.view.SurfaceHolder
import de.zwegen.zpaint.ui.MAX_SCALE
import de.zwegen.zpaint.ui.MIN_SCALE
import de.zwegen.zpaint.ui.Perspective
import org.junit.Before
import org.mockito.Mockito
import org.junit.Assert
import org.junit.Test
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class PerspectiveTests {
    @Mock
    private val holder: SurfaceHolder? = null

    @Mock
    private val canvas: Canvas? = null
    private var perspective: Perspective? = null

    @Before
    fun setUp() {
        val surfaceFrame = Mockito.mock(Rect::class.java)

        surfaceFrame.right = SURFACE_WIDTH
        surfaceFrame.bottom = SURFACE_HEIGHT
        Mockito.`when`(surfaceFrame.exactCenterX()).thenReturn(EXACT_CENTER_X)
        Mockito.`when`(surfaceFrame.exactCenterY()).thenReturn(EXACT_CENTER_Y)
        Mockito.`when`(holder?.surfaceFrame).thenReturn(surfaceFrame)
        perspective = Perspective(0, 0)
        perspective?.setSurfaceFrame(surfaceFrame)
    }

    @Test
    fun testInitialize() {
        val surfaceWidth = perspective?.surfaceWidth?.toFloat()
        val surfaceHeight = perspective?.surfaceHeight?.toFloat()

        if (surfaceWidth != null) { Assert.assertEquals(SURFACE_WIDTH.toFloat(), surfaceWidth, Float.MIN_VALUE) }
        if (surfaceHeight != null) { Assert.assertEquals(SURFACE_HEIGHT.toFloat(), surfaceHeight, Float.MIN_VALUE) }

        val surfaceCenterX = perspective?.surfaceCenterX
        val surfaceCenterY = perspective?.surfaceCenterY

        if (surfaceCenterX != null) { Assert.assertEquals(EXACT_CENTER_X, surfaceCenterX, Float.MIN_VALUE) }
        if (surfaceCenterY != null) { Assert.assertEquals(EXACT_CENTER_Y, surfaceCenterY, Float.MIN_VALUE) }

        Assert.assertEquals(INITIAL_SCALE, perspective!!.scale, Float.MIN_VALUE)
        Assert.assertNotEquals(0, perspective!!.surfaceTranslationX)
        Assert.assertNotEquals(0, perspective!!.surfaceTranslationY)
    }

    @Test
    fun testMultiplyScale() {
        val scale = 1.5f

        perspective?.multiplyScale(scale)
        perspective?.scale?.let { Assert.assertEquals(scale, it, Float.MIN_VALUE) }
        perspective?.applyToCanvas(canvas!!)

        val inOrder = Mockito.inOrder(canvas)

        inOrder.verify(canvas)?.scale(scale, scale, EXACT_CENTER_X, EXACT_CENTER_Y)
        inOrder.verify(canvas)
            ?.translate(perspective!!.surfaceTranslationX, perspective!!.surfaceTranslationY)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun testMultiplyScaleBelowMinimum() {
        val minScale: Float = MIN_SCALE

        perspective!!.multiplyScale(minScale * 0.9f)
        Assert.assertEquals(minScale, perspective!!.scale, Float.MIN_VALUE)
    }

    @Test
    fun testMultiplyScaleAboveMaximum() {
        val maxScale: Float = MAX_SCALE

        perspective!!.multiplyScale(maxScale * 1.1f)
        Assert.assertEquals(maxScale, perspective!!.scale, Float.MIN_VALUE)
    }

    @Test
    fun testSetScale() {
        val scale = 1.5f

        perspective?.scale = scale
        Assert.assertEquals(scale, perspective!!.scale, Float.MIN_VALUE)
        perspective?.applyToCanvas(canvas!!)

        val inOrder = Mockito.inOrder(canvas)

        inOrder.verify(canvas)?.scale(scale, scale, EXACT_CENTER_X, EXACT_CENTER_Y)
        perspective?.surfaceTranslationX?.let {
            perspective?.surfaceTranslationY?.let { it1 ->
                inOrder.verify(canvas)
                    ?.translate(it, it1)
            }
        }
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun testSetScaleBelowMinimum() {
        val minScale: Float = MIN_SCALE

        perspective?.scale = minScale * 0.9f
        perspective?.scale?.let { Assert.assertEquals(minScale, it, Float.MIN_VALUE) }
    }

    @Test
    fun testSetScaleAboveMaximum() {
        val maxScale: Float = MAX_SCALE

        perspective?.scale = maxScale * 1.1f
        perspective?.scale?.let { Assert.assertEquals(maxScale, it, Float.MIN_VALUE) }
    }

    @Test
    fun resetScaleAndTranslation_keepsFittedPortraitAtActualSurfaceSize() {
        perspective = Perspective(810, 1080)
        perspective?.setSurfaceFrame(surfaceFrame(1080, 1600))

        perspective?.resetScaleAndTranslation()

        Assert.assertEquals(1f, perspective!!.scale, Float.MIN_VALUE)
    }

    @Test
    fun resetScaleAndTranslation_fitsLargePortraitToSurfaceWidth() {
        perspective = Perspective(1, 1)
        val surfaceFrame = Mockito.mock(Rect::class.java)
        surfaceFrame.right = 1080
        surfaceFrame.bottom = 1600
        Mockito.`when`(surfaceFrame.exactCenterX()).thenReturn(540f)
        Mockito.`when`(surfaceFrame.exactCenterY()).thenReturn(800f)
        perspective?.setSurfaceFrame(surfaceFrame)
        perspective?.setBitmapDimensions(2160, 3840)

        perspective?.resetScaleAndTranslation()

        Assert.assertEquals(0.5f, perspective!!.scale, Float.MIN_VALUE)
    }

    @Test
    fun resetScaleAndTranslation_doesNotUpscaleSmallBitmap() {
        perspective = Perspective(800, 600)
        perspective?.setSurfaceFrame(surfaceFrame(1080, 1600))

        perspective?.resetScaleAndTranslation()

        Assert.assertEquals(1f, perspective!!.scale, Float.MIN_VALUE)
    }

    @Test
    fun resetScaleAndTranslationToFitWidth_upscalesSmallImageToVisibleDrawingSurfaceWidth() {
        perspective = Perspective(800, 1200)
        perspective?.setSurfaceFrame(surfaceFrame(1080, 1600))

        perspective?.resetScaleAndTranslationToFitWidth()

        Assert.assertEquals(1.35f, perspective!!.scale, Float.MIN_VALUE)
    }

    @Test
    fun resetScaleAndTranslationToFitWidth_downscalesLargeImageToVisibleDrawingSurfaceWidth() {
        perspective = Perspective(2160, 3840)
        perspective?.setSurfaceFrame(surfaceFrame(1080, 1600))

        perspective?.resetScaleAndTranslationToFitWidth()

        Assert.assertEquals(0.5f, perspective!!.scale, Float.MIN_VALUE)
    }

    @Test
    fun calculateZoomFactor_usesVisibleSurfaceHeightForPortraitImages() {
        perspective = Perspective(800, 1200)
        perspective?.setSurfaceFrame(surfaceFrame(1080, 1600))

        Assert.assertEquals(1600f / 1200f, perspective!!.calculateZoomFactor(), Float.MIN_VALUE)
    }

    @Test
    fun testSurfaceCenterYNotChangedWhenSurfaceFrameCenterYIsLower() {
        perspective?.setSurfaceFrame(Rect(0, 0, SURFACE_WIDTH, SURFACE_HEIGHT - 20))
        Assert.assertEquals(50.0f, perspective?.surfaceCenterY)
    }

    companion object {
        private const val SURFACE_WIDTH = 10
        private const val SURFACE_HEIGHT = 100
        private const val EXACT_CENTER_X = 5f
        private const val EXACT_CENTER_Y = 50f
        const val INITIAL_SCALE = 1f

        private fun surfaceFrame(width: Int, height: Int): Rect = Rect().apply {
            right = width
            bottom = height
        }
    }
}
