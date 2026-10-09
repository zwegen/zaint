package de.zwegen.zpaint.tools.implementation

import org.junit.Assert.assertEquals
import org.junit.Test

class SmudgeStrokeBoundsTest {
    @Test
    fun pointInTheMiddleUsesPaddedAreaSmallerThanCanvas() {
        assertEquals(
            SmudgeStrokeBounds(84, 84, 116, 116),
            SmudgeStrokeBounds.calculate(
                canvasWidth = 640,
                canvasHeight = 480,
                pointPath = listOf(SmudgePoint(100f, 100f)),
                maxSize = 30f
            )
        )
    }

    @Test
    fun strokeAtImageEdgeIsPaddedAndClippedToCanvas() {
        assertEquals(
            SmudgeStrokeBounds(0, 0, 26, 26),
            SmudgeStrokeBounds.calculate(
                canvasWidth = 640,
                canvasHeight = 480,
                pointPath = listOf(SmudgePoint(2f, 2f), SmudgePoint(10f, 10f)),
                maxSize = 30f
            )
        )
    }

    @Test
    fun longStrokeCoversEveryStampWithPadding() {
        assertEquals(
            SmudgeStrokeBounds(84, 84, 316, 216),
            SmudgeStrokeBounds.calculate(
                canvasWidth = 640,
                canvasHeight = 480,
                pointPath = listOf(SmudgePoint(100f, 100f), SmudgePoint(300f, 200f)),
                maxSize = 30f
            )
        )
    }
}
