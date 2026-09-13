package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.gpu.GpuFilterEngine
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.sqrt

enum class FillGradientDirection {
    TOP_BOTTOM,
    LEFT_RIGHT,
    LEFT_TOP_RIGHT_BOTTOM,
    RIGHT_TOP_LEFT_BOTTOM,
    RADIAL,
    RANDOM_WAVE,
    BOTTOM_TOP,
    RIGHT_LEFT,
    LEFT_BOTTOM_RIGHT_TOP,
    RIGHT_BOTTOM_LEFT_TOP,
    WAVE_LEFT_TOP_RIGHT_BOTTOM,
    WAVE_LEFT_RIGHT,
    WAVE_LEFT_BOTTOM_RIGHT_TOP,
    WAVE_BOTTOM_TOP,
    WAVE_RIGHT_BOTTOM_LEFT_TOP,
    WAVE_RIGHT_LEFT,
    WAVE_RIGHT_TOP_LEFT_BOTTOM,
    RADIAL_OUTSIDE_IN
}

class GradientFillCommand(
    clickedPixel: Point,
    paint: Paint,
    colorTolerance: Float,
    colors: IntArray,
    direction: FillGradientDirection,
    private val waveSeed: Int = createWaveSeed(clickedPixel, colors, direction)
) : Command {
    var clickedPixel = clickedPixel; private set
    var paint = paint; private set
    var colorTolerance = colorTolerance; private set
    var colors = sanitizeGradientColors(colors); private set
    val startColor: Int
        get() = colors.first()
    val endColor: Int
        get() = colors.last()
    var direction = direction; private set

    constructor(
        clickedPixel: Point,
        paint: Paint,
        colorTolerance: Float,
        startColor: Int,
        endColor: Int,
        direction: FillGradientDirection
    ) : this(clickedPixel, paint, colorTolerance, intArrayOf(startColor, endColor), direction)

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val currentLayer = layerModel.currentLayer ?: return
        val bitmap = currentLayer.bitmap
        if (!isInsideBitmap(bitmap, clickedPixel)) {
            return
        }

        val mask = createFillMask(bitmap)
        if (mask.bounds.isEmpty) {
            return
        }
        applyGradient(bitmap, mask)
    }

    private fun createFillMask(bitmap: Bitmap): FillMask {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val colorToBeReplaced = pixels[clickedPixel.y * width + clickedPixel.x]
        val visited = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()
        val mask = BooleanArray(width * height)
        val bounds = FillBounds()
        val toleranceSquared = square(colorTolerance.toInt())
        val considerTolerance = colorTolerance > 0

        queue.add(clickedPixel.y * width + clickedPixel.x)
        visited[clickedPixel.y * width + clickedPixel.x] = true

        while (!queue.isEmpty()) {
            val index = queue.removeFirst()
            val color = pixels[index]
            if (!shouldFill(color, colorToBeReplaced, toleranceSquared, considerTolerance)) {
                continue
            }

            mask[index] = true
            val x = index % width
            val y = index / width
            bounds.include(x, y)

            addNeighbor(index - 1, x > 0, visited, queue)
            addNeighbor(index + 1, x < width - 1, visited, queue)
            addNeighbor(index - width, y > 0, visited, queue)
            addNeighbor(index + width, y < height - 1, visited, queue)
        }

        return FillMask(mask, bounds)
    }

    private fun addNeighbor(
        index: Int,
        valid: Boolean,
        visited: BooleanArray,
        queue: ArrayDeque<Int>
    ) {
        if (valid && !visited[index]) {
            visited[index] = true
            queue.add(index)
        }
    }

    private fun applyGradient(bitmap: Bitmap, mask: FillMask) {
        if (colors.size == TWO_COLORS && !direction.isWaveGradient() &&
            shouldUseGpuGradient(mask.bounds)
        ) {
            try {
                applyGpuGradient(bitmap, mask)
                return
            } catch (error: RuntimeException) {
                // Fall back to CPU if the device cannot create the temporary GL surface.
            }
        }
        applyCpuGradient(bitmap, mask)
    }

    private fun shouldUseGpuGradient(bounds: FillBounds): Boolean {
        val width = bounds.right - bounds.left + 1
        val height = bounds.bottom - bounds.top + 1
        return width * height >= MIN_GPU_GRADIENT_PIXELS
    }

    private fun applyGpuGradient(bitmap: Bitmap, mask: FillMask) {
        val width = bitmap.width
        val bounds = mask.bounds
        val segmentWidth = bounds.right - bounds.left + 1
        val segmentHeight = bounds.bottom - bounds.top + 1
        val gradient = GpuFilterEngine.createGradient(
            segmentWidth,
            segmentHeight,
            colors.first(),
            colors.last(),
            direction
        )
        try {
            val row = IntArray(segmentWidth)
            val gradientRow = IntArray(segmentWidth)
            for (y in bounds.top..bounds.bottom) {
                val gradientY = y - bounds.top
                bitmap.getPixels(row, 0, segmentWidth, bounds.left, y, segmentWidth, 1)
                gradient.getPixels(gradientRow, 0, segmentWidth, 0, gradientY, segmentWidth, 1)
                var rowChanged = false
                for (x in bounds.left..bounds.right) {
                    val index = y * width + x
                    if (!mask.pixels[index]) {
                        continue
                    }
                    row[x - bounds.left] = gradientRow[x - bounds.left]
                    rowChanged = true
                }
                if (rowChanged) {
                    bitmap.setPixels(row, 0, segmentWidth, bounds.left, y, segmentWidth, 1)
                }
            }
        } finally {
            gradient.recycle()
        }
    }

    private fun applyCpuGradient(bitmap: Bitmap, mask: FillMask) {
        val width = bitmap.width
        val bounds = mask.bounds
        val segmentWidth = bounds.right - bounds.left + 1
        val row = IntArray(segmentWidth)
        val gradientColors = GradientColors(colors)
        val denominator = max(1f, gradientDenominator(bounds))

        for (y in bounds.top..bounds.bottom) {
            bitmap.getPixels(row, 0, segmentWidth, bounds.left, y, segmentWidth, 1)
            var rowChanged = false
            for (x in bounds.left..bounds.right) {
                val index = y * width + x
                if (!mask.pixels[index]) {
                    continue
                }
                val numerator = gradientNumerator(x, y, bounds)
                row[x - bounds.left] = gradientColors.blend(numerator / denominator)
                rowChanged = true
            }
            if (rowChanged) {
                bitmap.setPixels(row, 0, segmentWidth, bounds.left, y, segmentWidth, 1)
            }
        }
    }

    private fun gradientNumerator(x: Int, y: Int, bounds: FillBounds): Float {
        if (direction.isRadialGradient()) {
            val distance = radialDistanceFromCenter(x, y, bounds)
            return if (direction == FillGradientDirection.RADIAL_OUTSIDE_IN) {
                radialMaxDistance(bounds) - distance
            } else {
                distance
            }
        }
        val axis = direction.axis()
        val linear = linearGradientNumerator(x, y, bounds, axis)
        if (!direction.isWaveGradient()) {
            return linear
        }
        val amplitude = randomWaveAmplitude(bounds, axis)
        return linear - randomWaveOffset(wavePosition(x, y, bounds, axis), amplitude) + amplitude
    }

    private fun gradientDenominator(bounds: FillBounds): Float {
        if (direction.isRadialGradient()) {
            return radialMaxDistance(bounds)
        }
        val axis = direction.axis()
        val linear = linearGradientDenominator(bounds, axis)
        return if (direction.isWaveGradient()) {
            linear + 2f * randomWaveAmplitude(bounds, axis)
        } else {
            linear
        }
    }

    private fun linearGradientNumerator(
        x: Int,
        y: Int,
        bounds: FillBounds,
        axis: GradientAxis
    ): Float {
        val horizontal = when (axis.horizontal) {
            1 -> x - bounds.left
            -1 -> bounds.right - x
            else -> 0
        }
        val vertical = when (axis.vertical) {
            1 -> y - bounds.top
            -1 -> bounds.bottom - y
            else -> 0
        }
        return (horizontal + vertical).toFloat()
    }

    private fun linearGradientDenominator(bounds: FillBounds, axis: GradientAxis): Float {
        val width = if (axis.horizontal == 0) 0 else bounds.right - bounds.left
        val height = if (axis.vertical == 0) 0 else bounds.bottom - bounds.top
        return (width + height).toFloat()
    }

    private fun randomWaveAmplitude(bounds: FillBounds, axis: GradientAxis): Float =
        max(1f, linearGradientDenominator(bounds, axis) * RANDOM_WAVE_MAX_AMPLITUDE)

    private fun wavePosition(x: Int, y: Int, bounds: FillBounds, axis: GradientAxis): Float {
        val width = max(1, bounds.right - bounds.left).toFloat()
        val height = max(1, bounds.bottom - bounds.top).toFloat()
        val horizontal = ((x - bounds.left) / width).coerceIn(0f, 1f)
        val vertical = ((y - bounds.top) / height).coerceIn(0f, 1f)
        return when {
            axis.horizontal == 0 -> horizontal
            axis.vertical == 0 -> vertical
            axis.horizontal == axis.vertical -> ((horizontal - vertical) + 1f) / 2f
            else -> (horizontal + vertical) / 2f
        }
    }

    private fun randomWaveOffset(position: Float, amplitude: Float): Float {
        val scaled = position * RANDOM_WAVE_SEGMENTS
        val segment = scaled.toInt().coerceAtMost(RANDOM_WAVE_SEGMENTS - 1)
        val local = scaled - segment
        val start = randomWaveAnchor(segment)
        val end = randomWaveAnchor(segment + 1)
        val smooth = local * local * (3f - 2f * local)
        return (start + (end - start) * smooth) * amplitude
    }

    private fun FillGradientDirection.isWaveGradient(): Boolean = when (this) {
        FillGradientDirection.RANDOM_WAVE,
        FillGradientDirection.WAVE_LEFT_TOP_RIGHT_BOTTOM,
        FillGradientDirection.WAVE_LEFT_RIGHT,
        FillGradientDirection.WAVE_LEFT_BOTTOM_RIGHT_TOP,
        FillGradientDirection.WAVE_BOTTOM_TOP,
        FillGradientDirection.WAVE_RIGHT_BOTTOM_LEFT_TOP,
        FillGradientDirection.WAVE_RIGHT_LEFT,
        FillGradientDirection.WAVE_RIGHT_TOP_LEFT_BOTTOM -> true
        else -> false
    }

    private fun FillGradientDirection.isRadialGradient(): Boolean =
        this == FillGradientDirection.RADIAL || this == FillGradientDirection.RADIAL_OUTSIDE_IN

    private fun FillGradientDirection.axis(): GradientAxis = when (this) {
        FillGradientDirection.TOP_BOTTOM,
        FillGradientDirection.RANDOM_WAVE -> GradientAxis(0, 1)
        FillGradientDirection.LEFT_RIGHT,
        FillGradientDirection.WAVE_LEFT_RIGHT -> GradientAxis(1, 0)
        FillGradientDirection.LEFT_TOP_RIGHT_BOTTOM,
        FillGradientDirection.WAVE_LEFT_TOP_RIGHT_BOTTOM -> GradientAxis(1, 1)
        FillGradientDirection.RIGHT_TOP_LEFT_BOTTOM,
        FillGradientDirection.WAVE_RIGHT_TOP_LEFT_BOTTOM -> GradientAxis(-1, 1)
        FillGradientDirection.BOTTOM_TOP,
        FillGradientDirection.WAVE_BOTTOM_TOP -> GradientAxis(0, -1)
        FillGradientDirection.RIGHT_LEFT,
        FillGradientDirection.WAVE_RIGHT_LEFT -> GradientAxis(-1, 0)
        FillGradientDirection.LEFT_BOTTOM_RIGHT_TOP,
        FillGradientDirection.WAVE_LEFT_BOTTOM_RIGHT_TOP -> GradientAxis(1, -1)
        FillGradientDirection.RIGHT_BOTTOM_LEFT_TOP,
        FillGradientDirection.WAVE_RIGHT_BOTTOM_LEFT_TOP -> GradientAxis(-1, -1)
        FillGradientDirection.RADIAL,
        FillGradientDirection.RADIAL_OUTSIDE_IN -> error("Radial gradients do not have a linear axis")
    }

    private data class GradientAxis(val horizontal: Int, val vertical: Int)

    private fun randomWaveAnchor(index: Int): Float {
        var value = waveSeed xor (index * WAVE_HASH_STEP)
        value = value xor (value ushr 16)
        value *= WAVE_HASH_MULTIPLIER
        value = value xor (value ushr 15)
        val positive = value and Int.MAX_VALUE
        return positive / Int.MAX_VALUE.toFloat() * 2f - 1f
    }

    private fun radialDistanceFromCenter(x: Int, y: Int, bounds: FillBounds): Float {
        val dx = x - bounds.centerX
        val dy = y - bounds.centerY
        return sqrt(dx * dx + dy * dy)
    }

    private fun radialMaxDistance(bounds: FillBounds): Float = maxOf(
        radialDistanceFromCenter(bounds.left, bounds.top, bounds),
        radialDistanceFromCenter(bounds.right, bounds.top, bounds),
        radialDistanceFromCenter(bounds.left, bounds.bottom, bounds),
        radialDistanceFromCenter(bounds.right, bounds.bottom, bounds)
    )

    private fun shouldFill(
        pixel: Int,
        referenceColor: Int,
        toleranceSquared: Int,
        considerTolerance: Boolean
    ): Boolean =
        pixel == referenceColor || considerTolerance && isPixelWithinColorTolerance(
            pixel,
            referenceColor,
            toleranceSquared
        )

    private fun isPixelWithinColorTolerance(
        pixel: Int,
        referenceColor: Int,
        toleranceSquared: Int
    ): Boolean {
        val redDiff = pixel.red - referenceColor.red
        val greenDiff = pixel.green - referenceColor.green
        val blueDiff = pixel.blue - referenceColor.blue
        val alphaDiff = pixel.alpha - referenceColor.alpha
        return square(redDiff) + square(greenDiff) + square(blueDiff) + square(alphaDiff) <=
            toleranceSquared
    }

    private fun square(x: Int) = x * x

    private fun isInsideBitmap(bitmap: Bitmap, point: Point): Boolean =
        point.x in 0 until bitmap.width && point.y in 0 until bitmap.height

    override fun freeResources() {
        // No resources to free
    }

    private data class FillMask(val pixels: BooleanArray, val bounds: FillBounds)

    private class FillBounds {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE

        val isEmpty: Boolean
            get() = left == Int.MAX_VALUE
        val centerX: Float
            get() = (left + right) / 2f
        val centerY: Float
            get() = (top + bottom) / 2f

        fun include(x: Int, y: Int) {
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }

    private class GradientColors(private val colors: IntArray) {

        fun blend(fraction: Float): Int {
            val clamped = fraction.coerceIn(0f, 1f)
            val scaled = clamped * (colors.size - 1)
            val startIndex = scaled.toInt().coerceAtMost(colors.lastIndex - 1)
            val localFraction = scaled - startIndex
            val startColor = colors[startIndex]
            val endColor = colors[startIndex + 1]
            val startAlpha = alpha(startColor)
            val startRed = red(startColor)
            val startGreen = green(startColor)
            val startBlue = blue(startColor)
            val alphaDelta = alpha(endColor) - startAlpha
            val redDelta = red(endColor) - startRed
            val greenDelta = green(endColor) - startGreen
            val blueDelta = blue(endColor) - startBlue
            val alpha = startAlpha + (alphaDelta * localFraction).toInt()
            val red = startRed + (redDelta * localFraction).toInt()
            val green = startGreen + (greenDelta * localFraction).toInt()
            val blue = startBlue + (blueDelta * localFraction).toInt()
            return (alpha shl ALPHA_SHIFT) or
                (red shl RED_SHIFT) or
                (green shl GREEN_SHIFT) or
                blue
        }
    }

    private val Int.alpha: Int
        get() = (this ushr ALPHA_SHIFT) and BYTE_MASK
    private val Int.red: Int
        get() = (this ushr RED_SHIFT) and BYTE_MASK
    private val Int.green: Int
        get() = (this ushr GREEN_SHIFT) and BYTE_MASK
    private val Int.blue: Int
        get() = this and BYTE_MASK

    private companion object {
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BYTE_MASK = 0xFF
        const val MIN_GPU_GRADIENT_PIXELS = 4096
        const val TWO_COLORS = 2
        const val MAX_GRADIENT_COLORS = 3
        const val RANDOM_WAVE_MAX_AMPLITUDE = 0.17f
        const val RANDOM_WAVE_SEGMENTS = 2
        const val WAVE_HASH_STEP = 0x45d9f3b
        const val WAVE_HASH_MULTIPLIER = 0x27d4eb2d

        fun alpha(color: Int): Int = (color ushr ALPHA_SHIFT) and BYTE_MASK

        fun red(color: Int): Int = (color ushr RED_SHIFT) and BYTE_MASK

        fun green(color: Int): Int = (color ushr GREEN_SHIFT) and BYTE_MASK

        fun blue(color: Int): Int = color and BYTE_MASK

        fun sanitizeGradientColors(colors: IntArray): IntArray {
            if (colors.isEmpty()) {
                return intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            }
            val sanitized = colors.take(MAX_GRADIENT_COLORS).toIntArray()
            return if (sanitized.size >= TWO_COLORS) {
                sanitized
            } else {
                intArrayOf(sanitized.first(), sanitized.first())
            }
        }

        fun createWaveSeed(
            clickedPixel: Point,
            colors: IntArray,
            direction: FillGradientDirection
        ): Int {
            var seed = System.nanoTime().toInt()
            seed = 31 * seed + clickedPixel.x
            seed = 31 * seed + clickedPixel.y
            seed = 31 * seed + direction.ordinal
            colors.forEach { color ->
                seed = 31 * seed + color
            }
            return seed
        }
    }
}
