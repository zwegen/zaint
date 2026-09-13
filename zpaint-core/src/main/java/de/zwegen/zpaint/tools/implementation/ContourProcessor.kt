package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Color
import boofcv.alg.filter.binary.BinaryImageOps
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors
import boofcv.struct.image.GrayS16
import boofcv.struct.image.GrayU8

/**
 * Extracts dark black/grey drawing lines onto transparency.
 *
 * The tolerance controls the brightest grey that still belongs to a line. The
 * adaptive version works at up to twice the source resolution, additionally
 * keeps locally darker lines, and reduces only isolated noise. This preserves
 * faint drawing outlines without globally making every line thicker.
 */
object ContourProcessor {
    fun extract(source: Bitmap, tolerance: Int): Bitmap {
        val processingSource = createProcessingBitmap(source)
        val contour = extractAtProcessingScale(processingSource, tolerance)
        if (processingSource === source) {
            return fillTinyEnclosedHoles(contour)
        }
        processingSource.recycle()
        return downscalePreservingThinLines(contour, source.width, source.height).also {
            contour.recycle()
        }.let(::fillTinyEnclosedHoles)
    }

    private fun extractAtProcessingScale(source: Bitmap, tolerance: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height).also { source.getPixels(it, 0, width, 0, 0, width, height) }
        val threshold = tolerance.coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)
        val detected = BooleanArray(pixels.size) { index ->
            val color = pixels[index]
            Color.alpha(color) > 0 &&
                (luminance(color) <= threshold || isLocallyDarker(pixels, index, width, height))
        }
        val closed = closeShortAlignedGaps(detected, width, height)
        val background = findBorderConnectedFlatRegions(pixels, width, height)
        val bridged = addCannyBridges(closed, pixels, background, width, height, threshold)
        val enhanced = cleanPointNoise(removeLooseCannyTips(bridged, closed, width, height), width, height)
        val filledGaps = fillSupportedGaps(enhanced, width, height)
        val refined = removeUnsupportedBlackPixels(filledGaps, width, height)
        val output = IntArray(pixels.size)
        for (index in output.indices) {
            if (!refined[index] || neighbourCount(refined, index % width, index / width, width, height) == 0) {
                continue
            }
            output[index] = Color.argb(Color.alpha(pixels[index]), 0, 0, 0)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    private fun createProcessingBitmap(source: Bitmap): Bitmap {
        val sourcePixels = source.width.toLong() * source.height
        val scale = minOf(MAX_SCALE, kotlin.math.sqrt(MAX_PROCESSING_PIXELS.toDouble() / sourcePixels).toFloat())
        if (scale <= 1f) return source
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Bilinear scaling averages a one-pixel source line with transparency and
     * can make an otherwise continuous fine contour look faint or broken.
     * Keep the strongest contour pixel that covers each destination pixel
     * instead. This protects fine lines without applying a global thickening.
     */
    private fun downscalePreservingThinLines(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourcePixels = IntArray(source.width * source.height).also {
            source.getPixels(it, 0, source.width, 0, 0, source.width, source.height)
        }
        val output = IntArray(targetWidth * targetHeight)
        for (targetY in 0 until targetHeight) {
            val startY = targetY * source.height / targetHeight
            val endY = ((targetY + 1) * source.height - 1) / targetHeight
            for (targetX in 0 until targetWidth) {
                val startX = targetX * source.width / targetWidth
                val endX = ((targetX + 1) * source.width - 1) / targetWidth
                var strongestAlpha = 0
                for (sourceY in startY..endY) {
                    for (sourceX in startX..endX) {
                        strongestAlpha = maxOf(strongestAlpha, Color.alpha(sourcePixels[sourceY * source.width + sourceX]))
                    }
                }
                if (strongestAlpha > 0) output[targetY * targetWidth + targetX] = Color.argb(strongestAlpha, 0, 0, 0)
            }
        }
        return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        }
    }

    /**
     * Fills transparent pixels supported by at least six black neighbours.
     * This removes isolated white specks and narrow white rills without
     * changing the outer contour boundary.
     */
    private fun fillTinyEnclosedHoles(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height).also {
            bitmap.getPixels(it, 0, width, 0, 0, width, height)
        }
        val filled = fillSupportedGaps(
            BooleanArray(pixels.size) { index -> Color.alpha(pixels[index]) > 0 },
            width,
            height
        )
        var changed = false
        for (index in filled.indices) {
            if (filled[index] && Color.alpha(pixels[index]) == 0) {
                pixels[index] = Color.BLACK
                changed = true
            }
        }
        if (changed) bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    internal fun fillSupportedGaps(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val result = mask.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (!mask[index] && neighbourCount(mask, x, y, width, height) >= MIN_BLACK_NEIGHBOURS) {
                    result[index] = true
                }
            }
        }
        return result
    }

    /**
     * Removes a black pixel only when it is a protruding artefact rather than
     * carrying a thin line. Its black neighbours must remain locally connected
     * by four-directional steps without the candidate pixel.
     */
    internal fun removeUnsupportedBlackPixels(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val result = mask.copyOf()
        val neighbours = IntArray(MAX_NEIGHBOURS)
        val queue = IntArray(MAX_NEIGHBOURS)
        val visited = BooleanArray(MAX_NEIGHBOURS)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                if (mask[index] &&
                    neighbourCount(mask, x, y, width, height) <= MAX_BLACK_NEIGHBOURS_FOR_REMOVAL &&
                    blackNeighboursRemainConnected(mask, x, y, width, height, neighbours, queue, visited)
                ) {
                    result[index] = false
                }
            }
        }
        return result
    }

    private fun blackNeighboursRemainConnected(
        mask: BooleanArray,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        neighbours: IntArray,
        queue: IntArray,
        visited: BooleanArray
    ): Boolean {
        var count = 0
        for (offsetY in -1..1) {
            for (offsetX in -1..1) {
                if (offsetX == 0 && offsetY == 0) continue
                val neighbourX = x + offsetX
                val neighbourY = y + offsetY
                if (neighbourX in 0 until width && neighbourY in 0 until height &&
                    mask[neighbourY * width + neighbourX]
                ) {
                    neighbours[count++] = neighbourY * width + neighbourX
                }
            }
        }
        if (count < MIN_BLACK_NEIGHBOURS_FOR_REMOVAL) return false
        java.util.Arrays.fill(visited, false)
        visited[0] = true
        queue[0] = 0
        var head = 0
        var tail = 1
        while (head < tail) {
            val current = neighbours[queue[head++]]
            val currentX = current % width
            val currentY = current / width
            for (candidate in 0 until count) {
                val next = neighbours[candidate]
                if (!visited[candidate] &&
                    kotlin.math.abs(currentX - next % width) + kotlin.math.abs(currentY - next / width) == 1
                ) {
                    visited[candidate] = true
                    queue[tail++] = candidate
                }
            }
        }
        for (candidate in 0 until count) {
            if (!visited[candidate]) return false
        }
        return true
    }

    private fun addCannyBridges(
        mask: BooleanArray,
        pixels: IntArray,
        background: BooleanArray,
        width: Int,
        height: Int,
        tolerance: Int
    ): BooleanArray {
        val grayscale = GrayU8(width, height)
        for (index in pixels.indices) {
            grayscale.data[index] = luminance(pixels[index]).toByte()
        }
        val edges = GrayU8(width, height)
        val highThreshold = (HIGH_EDGE_THRESHOLD - tolerance / TOLERANCE_DIVISOR)
            .coerceIn(MIN_HIGH_EDGE_THRESHOLD, HIGH_EDGE_THRESHOLD)
        val lowThreshold = highThreshold / LOW_TO_HIGH_RATIO
        FactoryEdgeDetectors.canny(
            CANNY_BLUR_RADIUS,
            false,
            true,
            GrayU8::class.java,
            GrayS16::class.java
        ).process(grayscale, lowThreshold, highThreshold, edges)

        return mask.copyOf().also { result ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    if (!result[index] && edges.data[index].toInt() != 0 &&
                        isPreferredEdgeSide(pixels, index, width, height) &&
                        (isBetweenLineEnds(mask, x, y, width, height) ||
                            isNearLine(mask, x, y, width, height) ||
                            isObjectBackgroundBoundary(background, x, y, width, height))
                    ) {
                        result[index] = true
                    }
                }
            }
        }
    }

    /**
     * Finds quiet regions that can be reached from the image edge without
     * crossing a clear colour jump. This is only an extra hint for missing
     * outer contours; the existing detected-line mask is never changed here.
     */
    internal fun findBorderConnectedFlatRegions(pixels: IntArray, width: Int, height: Int): BooleanArray {
        val background = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var tail = 0
        fun addSeed(x: Int, y: Int) {
            val index = y * width + x
            if (!background[index] && isLocallyFlat(pixels, index, width, height)) {
                background[index] = true
                queue[tail++] = index
            }
        }
        for (x in 0 until width) {
            addSeed(x, 0)
            if (height > 1) addSeed(x, height - 1)
        }
        for (y in 1 until height - 1) {
            addSeed(0, y)
            if (width > 1) addSeed(width - 1, y)
        }
        var head = 0
        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            for ((offsetX, offsetY) in ORTHOGONAL_DIRECTIONS) {
                val neighbourX = x + offsetX
                val neighbourY = y + offsetY
                if (neighbourX !in 0 until width || neighbourY !in 0 until height) continue
                val neighbour = neighbourY * width + neighbourX
                if (!background[neighbour] &&
                    isLocallyFlat(pixels, neighbour, width, height) &&
                    colorDistance(pixels[index], pixels[neighbour]) <= MAX_BACKGROUND_COLOR_STEP
                ) {
                    background[neighbour] = true
                    queue[tail++] = neighbour
                }
            }
        }
        return background
    }

    private fun isLocallyFlat(pixels: IntArray, index: Int, width: Int, height: Int): Boolean {
        if (alpha(pixels[index]) == 0) return false
        val x = index % width
        val y = index / width
        var totalDifference = 0
        var count = 0
        for ((offsetX, offsetY) in ORTHOGONAL_DIRECTIONS) {
            val neighbourX = x + offsetX
            val neighbourY = y + offsetY
            if (neighbourX !in 0 until width || neighbourY !in 0 until height) continue
            val neighbour = pixels[neighbourY * width + neighbourX]
            if (alpha(neighbour) == 0) return false
            totalDifference += colorDistance(pixels[index], neighbour)
            count++
        }
        return count > 0 && totalDifference / count <= MAX_BACKGROUND_LOCAL_VARIATION
    }

    private fun isObjectBackgroundBoundary(background: BooleanArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        var hasBackground = false
        var hasNonBackground = false
        for ((offsetX, offsetY) in ORTHOGONAL_DIRECTIONS) {
            val neighbourX = x + offsetX
            val neighbourY = y + offsetY
            if (neighbourX !in 0 until width || neighbourY !in 0 until height) continue
            if (background[neighbourY * width + neighbourX]) hasBackground = true else hasNonBackground = true
        }
        return hasBackground && hasNonBackground
    }

    /**
     * Canny can detect both sides of a light-to-dark transition. Keep only the
     * darker side of that transition, which avoids adding a second, parallel
     * contour beside an existing drawing line.
     */
    private fun isPreferredEdgeSide(pixels: IntArray, index: Int, width: Int, height: Int): Boolean {
        val x = index % width
        val y = index / width
        val center = luminance(pixels[index])
        val neighbours = intArrayOf(
            if (x > 0) index - 1 else -1,
            if (x < width - 1) index + 1 else -1,
            if (y > 0) index - width else -1,
            if (y < height - 1) index + width else -1
        )
        return neighbours.any { neighbour ->
            neighbour >= 0 && Color.alpha(pixels[neighbour]) > 0 &&
                center + MIN_EDGE_SIDE_CONTRAST <= luminance(pixels[neighbour])
        }
    }

    private fun isBetweenLineEnds(mask: BooleanArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        val directions = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(0, 1),
            intArrayOf(1, 1),
            intArrayOf(1, -1)
        )
        for ((directionX, directionY) in directions) {
            if ((isLine(mask, x - 2 * directionX, y - 2 * directionY, width, height) &&
                    isLine(mask, x + directionX, y + directionY, width, height)) ||
                (isLine(mask, x - directionX, y - directionY, width, height) &&
                    isLine(mask, x + 2 * directionX, y + 2 * directionY, width, height))
            ) {
                return true
            }
        }
        return false
    }

    private fun isNearLine(mask: BooleanArray, x: Int, y: Int, width: Int, height: Int): Boolean {
        for (offsetY in -1..1) {
            for (offsetX in -1..1) {
                if ((offsetX != 0 || offsetY != 0) && isLine(mask, x + offsetX, y + offsetY, width, height)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Canny is useful for bridging a weak outline, but a one-pixel endpoint it
     * adds next to an existing contour reads as a small jag. Source pixels are
     * never removed; only unsupported Canny additions are discarded.
     */
    private fun removeLooseCannyTips(
        mask: BooleanArray,
        originalMask: BooleanArray,
        width: Int,
        height: Int
    ): BooleanArray {
        var result = mask
        repeat(MAX_CANNY_TIP_REMOVAL_PASSES) {
            val next = result.copyOf()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * width + x
                    if (result[index] && !originalMask[index] &&
                        neighbourCount(result, x, y, width, height) <= MAX_TIP_NEIGHBOURS
                    ) {
                        next[index] = false
                    }
                }
            }
            result = next
        }
        return result
    }

    private fun cleanPointNoise(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val input = GrayU8(width, height)
        for (index in mask.indices) {
            if (mask[index]) input.data[index] = BINARY_LINE_PIXEL
        }
        val cleaned = BinaryImageOps.removePointNoise(input, null)
        return BooleanArray(mask.size) { index -> cleaned.data[index].toInt() != 0 }
    }

    private fun isLine(mask: BooleanArray, x: Int, y: Int, width: Int, height: Int): Boolean =
        x in 0 until width && y in 0 until height && mask[y * width + x]

    /** Makes the source-line pixels transparent so the extracted contour can replace them above. */
    fun hideDetectedLines(source: Bitmap, tolerance: Int): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(output.width * output.height).also {
            output.getPixels(it, 0, output.width, 0, 0, output.width, output.height)
        }
        val threshold = tolerance.coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)
        for (index in pixels.indices) {
            val color = pixels[index]
            if (Color.alpha(color) > 0 && luminance(color) <= threshold) {
                pixels[index] = Color.TRANSPARENT
            }
        }
        output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        return output
    }

    /**
     * Closes only very short, straight interruptions in a contour. The mask is
     * always read from its original state, so a longer gap cannot be filled by
     * repeated passes. Both sides must be actual line ends; this keeps nearby
     * or branching contours, such as the straw at the mouth, separate.
     */
    internal fun closeShortAlignedGaps(mask: BooleanArray, width: Int, height: Int): BooleanArray =
        mask.copyOf().also { result ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (!isLine(mask, x, y, width, height) ||
                        neighbourCount(mask, x, y, width, height) != LINE_END_NEIGHBOURS
                    ) continue
                    for ((directionX, directionY) in SHORT_GAP_DIRECTIONS) {
                        for (gapLength in 1..MAX_SHORT_GAP_LENGTH) {
                            val endpointX = x + (gapLength + 1) * directionX
                            val endpointY = y + (gapLength + 1) * directionY
                            if (!isLine(mask, endpointX, endpointY, width, height) ||
                                neighbourCount(mask, endpointX, endpointY, width, height) != LINE_END_NEIGHBOURS
                            ) continue
                            for (step in 1..gapLength) {
                                result[(y + step * directionY) * width + x + step * directionX] = true
                            }
                        }
                    }
                }
            }
        }

    /**
     * Accepts a weak line when it is noticeably darker than its immediate
     * surroundings. This is deliberately local: a pale cup outline can remain,
     * while a uniformly grey area is not turned into a contour.
     */
    private fun isLocallyDarker(pixels: IntArray, index: Int, width: Int, height: Int): Boolean {
        val x = index % width
        val y = index / width
        if (x < LOCAL_RADIUS || x >= width - LOCAL_RADIUS || y < LOCAL_RADIUS || y >= height - LOCAL_RADIUS) {
            return false
        }
        val center = luminance(pixels[index])
        val neighbours = intArrayOf(
            index - LOCAL_RADIUS,
            index + LOCAL_RADIUS,
            index - LOCAL_RADIUS * width,
            index + LOCAL_RADIUS * width
        )
        var neighbourLuminance = 0
        for (neighbour in neighbours) {
            if (Color.alpha(pixels[neighbour]) == 0) return false
            neighbourLuminance += luminance(pixels[neighbour])
        }
        return center + MIN_LOCAL_CONTRAST <= neighbourLuminance / neighbours.size
    }

    private fun neighbourCount(mask: BooleanArray, x: Int, y: Int, width: Int, height: Int): Int {
        var count = 0
        for (offsetY in -1..1) {
            for (offsetX in -1..1) {
                if (offsetX == 0 && offsetY == 0) continue
                val neighbourX = x + offsetX
                val neighbourY = y + offsetY
                if (neighbourX in 0 until width && neighbourY in 0 until height &&
                    mask[neighbourY * width + neighbourX]
                ) count++
            }
        }
        return count
    }

    private fun luminance(color: Int): Int =
        (Color.red(color) * RED + Color.green(color) * GREEN + Color.blue(color) * BLUE).toInt()

    private fun colorDistance(first: Int, second: Int): Int =
        kotlin.math.abs(red(first) - red(second)) +
            kotlin.math.abs(green(first) - green(second)) +
            kotlin.math.abs(blue(first) - blue(second))

    private fun alpha(color: Int): Int = color ushr 24
    private fun red(color: Int): Int = color shr 16 and COLOR_COMPONENT_MASK
    private fun green(color: Int): Int = color shr 8 and COLOR_COMPONENT_MASK
    private fun blue(color: Int): Int = color and COLOR_COMPONENT_MASK

    private const val MIN_TOLERANCE = 0
    private const val MAX_TOLERANCE = 255
    private const val RED = 0.299f
    private const val GREEN = 0.587f
    private const val BLUE = 0.114f
    private const val CANNY_BLUR_RADIUS = 1
    private const val HIGH_EDGE_THRESHOLD = 0.35f
    private const val MIN_HIGH_EDGE_THRESHOLD = 0.10f
    private const val TOLERANCE_DIVISOR = 1020f
    private const val LOW_TO_HIGH_RATIO = 3f
    private const val BINARY_LINE_PIXEL: Byte = 1
    private const val MAX_SCALE = 2f
    private const val MAX_PROCESSING_PIXELS = 16_000_000L
    private const val LOCAL_RADIUS = 2
    private const val MIN_LOCAL_CONTRAST = 12
    private const val MIN_EDGE_SIDE_CONTRAST = 8
    private const val MAX_BACKGROUND_LOCAL_VARIATION = 30
    private const val MAX_BACKGROUND_COLOR_STEP = 48
    private const val COLOR_COMPONENT_MASK = 0xff
    private const val MAX_CANNY_TIP_REMOVAL_PASSES = 2
    private const val MAX_TIP_NEIGHBOURS = 1
    private const val MAX_SHORT_GAP_LENGTH = 2
    private const val LINE_END_NEIGHBOURS = 1
    private const val MIN_BLACK_NEIGHBOURS = 5
    private const val MIN_BLACK_NEIGHBOURS_FOR_REMOVAL = 2
    private const val MAX_BLACK_NEIGHBOURS_FOR_REMOVAL = 3
    private const val MAX_NEIGHBOURS = 8
    private val SHORT_GAP_DIRECTIONS = arrayOf(
        intArrayOf(1, 0),
        intArrayOf(0, 1),
        intArrayOf(1, 1),
        intArrayOf(1, -1)
    )
    private val ORTHOGONAL_DIRECTIONS = arrayOf(
        intArrayOf(-1, 0),
        intArrayOf(1, 0),
        intArrayOf(0, -1),
        intArrayOf(0, 1)
    )
}
