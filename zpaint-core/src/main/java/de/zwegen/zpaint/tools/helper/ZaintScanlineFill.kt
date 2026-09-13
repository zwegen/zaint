package de.zwegen.zpaint.tools.helper

import android.graphics.Bitmap
import android.graphics.Point
import androidx.annotation.VisibleForTesting
import java.util.ArrayDeque
import java.util.Queue

/**
 * Scanline flood fill for Android bitmaps.
 *
 * Each pending span knows which neighbouring row to inspect. The algorithm writes completed spans
 * straight back to the bitmap, keeping memory bounded to the pixel snapshot and pending spans.
 */
class ZaintScanlineFill : FillAlgorithm {
    @VisibleForTesting
    lateinit var pixels: Array<IntArray>

    @VisibleForTesting
    lateinit var clickedPixel: Point

    @VisibleForTesting
    val ranges: Queue<FillSpan> = ArrayDeque()

    @VisibleForTesting
    var targetColor = 0

    @VisibleForTesting
    var colorToBeReplaced = 0

    @VisibleForTesting
    var colorToleranceThresholdSquared = 0

    private lateinit var filledPixels: Array<BooleanArray>
    private lateinit var bitmap: Bitmap
    private lateinit var colorMatcher: FillColorMatcher
    private var width = 0
    private var height = 0

    override fun setParameters(
        bitmap: Bitmap,
        clickedPixel: Point,
        targetColor: Int,
        replacementColor: Int,
        colorToleranceThreshold: Float
    ) {
        this.bitmap = bitmap
        width = bitmap.width
        height = bitmap.height
        pixels = Array(height) { row -> IntArray(width).also { bitmap.getPixels(it, 0, width, 0, row, width, 1) } }
        filledPixels = Array(height) { BooleanArray(width) }
        this.clickedPixel = clickedPixel
        this.targetColor = targetColor
        colorToBeReplaced = replacementColor
        colorToleranceThresholdSquared = colorToleranceThreshold.toInt().let { it * it }
        colorMatcher = FillColorMatcher(replacementColor, colorToleranceThreshold)
        ranges.clear()
    }

    override fun performFilling() {
        if (!canFill(clickedPixel.y, clickedPixel.x)) {
            return
        }

        val origin = fillSpan(clickedPixel.y, clickedPixel.x, ScanDirection.UP)
        ranges += origin
        ranges += origin.copy(direction = ScanDirection.DOWN)

        while (ranges.isNotEmpty()) {
            inspectNeighbouringRow(ranges.remove())
        }
    }

    private fun inspectNeighbouringRow(parent: FillSpan) {
        val row = parent.row + parent.direction.rowOffset
        if (row !in 0 until height) {
            return
        }

        var column = parent.start
        while (column <= parent.end) {
            if (!canFill(row, column)) {
                column++
                continue
            }

            val child = fillSpan(row, column, parent.direction)
            ranges += child
            enqueueOppositeExtension(parent, child)
            column = child.end + 1
        }
    }

    private fun enqueueOppositeExtension(parent: FillSpan, child: FillSpan) {
        if (child.start <= parent.start - 2) {
            ranges += FillSpan(child.row, child.start, parent.start - 2, parent.direction.opposite())
        }
        if (child.end >= parent.end + 2) {
            ranges += FillSpan(child.row, parent.end + 2, child.end, parent.direction.opposite())
        }
    }

    private fun fillSpan(row: Int, column: Int, direction: ScanDirection): FillSpan {
        var start = column
        while (start > 0 && canFill(row, start - 1)) {
            start--
        }

        var end = column
        while (end + 1 < width && canFill(row, end + 1)) {
            end++
        }

        for (col in start..end) {
            pixels[row][col] = targetColor
            filledPixels[row][col] = true
        }
        bitmap.setPixels(pixels[row], start, width, start, row, end - start + 1, 1)
        return FillSpan(row, start, end, direction)
    }

    private fun canFill(row: Int, column: Int): Boolean =
        !filledPixels[row][column] && colorMatcher.matches(pixels[row][column])

    data class FillSpan(
        val row: Int,
        val start: Int,
        val end: Int,
        val direction: ScanDirection
    )

    enum class ScanDirection(val rowOffset: Int) {
        UP(-1),
        DOWN(1);

        fun opposite(): ScanDirection = if (this == UP) DOWN else UP
    }
}
