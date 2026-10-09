package de.zwegen.zpaint.tools.implementation

import org.junit.Assert.assertEquals
import org.junit.Test

class SoftBrushStrokeBoundsTest {
    @Test
    fun boundsIncludeStrokeBlurAndAntialiasPaddingAndClipAtCanvasEdge() {
        assertEquals(
            SoftBrushStrokeBounds(182, 132, 238, 171),
            SoftBrushStrokeBounds.calculate(
                canvasWidth = 640,
                canvasHeight = 480,
                pathLeft = 200f,
                pathTop = 150f,
                pathRight = 220f,
                pathBottom = 153f,
                strokeWidth = 12f,
                blurRadius = 3.36f
            )
        )
        assertEquals(
            SoftBrushStrokeBounds(0, 0, 20, 20),
            SoftBrushStrokeBounds.calculate(
                canvasWidth = 640,
                canvasHeight = 480,
                pathLeft = -5f,
                pathTop = -5f,
                pathRight = 2f,
                pathBottom = 2f,
                strokeWidth = 12f,
                blurRadius = 3.36f
            )
        )
    }
}
