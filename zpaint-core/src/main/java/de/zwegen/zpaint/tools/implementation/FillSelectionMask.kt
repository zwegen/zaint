package de.zwegen.zpaint.tools.implementation

import android.graphics.Rect
import java.util.ArrayDeque

/** Builds the final fill mask from all selected connected regions. */
internal object FillSelectionMask {
    fun combine(regions: List<ImageFillRegion>): BooleanArray {
        require(regions.isNotEmpty())
        val selected = BooleanArray(regions.first().pixels.size)
        regions.forEach { region ->
            region.pixels.forEachIndexed { index, isSelected ->
                if (isSelected) selected[index] = true
            }
        }
        fillSmallEnclosedIslands(selected, regions.first().canvasWidth)
        return selected
    }

    fun bounds(regions: List<ImageFillRegion>): Rect {
        val first = regions.firstOrNull() ?: return Rect()
        return regions.drop(1).fold(Rect(first.bounds)) { combined, next ->
            combined.apply { union(next.bounds) }
        }
    }

    /**
     * Returns the shared soft edge mask for image and color fills.
     *
     * The original image remains untouched outside the tolerance selection. The replacement
     * starts transparent at its edge and becomes opaque over the specified selected pixels.
     */
    fun alphaMask(regions: List<ImageFillRegion>, featherPixels: Int = IMAGE_FEATHER_PIXELS): IntArray {
        require(featherPixels >= 0)
        val selected = combine(regions)
        val width = regions.first().canvasWidth
        val insideDistances = insideDistances(selected, width, featherPixels)
        return IntArray(selected.size) { index ->
            when {
                insideDistances[index] in 1..featherPixels -> {
                    (insideDistances[index] * ALPHA_MAX / (featherPixels + 1))
                }
                selected[index] -> ALPHA_MAX
                else -> 0
            }
        }
    }


    /** Subtle one-pixel coverage smoothing for image fills; never softens the canvas edge. */
    fun antialiasedImageMask(regions: List<ImageFillRegion>): IntArray {
        val selected = combine(regions)
        val width = regions.first().canvasWidth
        val height = selected.size / width
        val weights = intArrayOf(1, 2, 1)
        return IntArray(selected.size) { index ->
            if (!selected[index]) return@IntArray 0
            val x = index % width
            val y = index / width
            // At the canvas boundary there is no neighbouring image to blend into.
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                return@IntArray ALPHA_MAX
            }
            var coverage = 0
            for (dy in -1..1) for (dx in -1..1) {
                if (x + dx !in 0 until width || y + dy !in 0 until height ||
                    selected[(y + dy) * width + x + dx]) {
                    coverage += weights[dy + 1] * weights[dx + 1]
                }
            }
            (coverage * ALPHA_MAX + 8) / 16
        }
    }

    /** Nearest original pixel on the unchanged side of each selected edge. */
    fun outsideColors(regions: List<ImageFillRegion>, original: IntArray, featherPixels: Int): IntArray {
        val selected = combine(regions)
        val width = regions.first().canvasWidth
        val height = selected.size / width
        require(original.size == selected.size)
        val colors = IntArray(selected.size)
        val distances = IntArray(selected.size)
        val queue = ArrayDeque<Int>()
        for (index in selected.indices) {
            if (!selected[index]) continue
            val x = index % width
            val y = index / width
            val outside = when {
                x > 0 && !selected[index - 1] -> index - 1
                x + 1 < width && !selected[index + 1] -> index + 1
                y > 0 && !selected[index - width] -> index - width
                y + 1 < height && !selected[index + width] -> index + width
                else -> -1
            }
            if (outside >= 0) {
                colors[index] = original[outside]
                distances[index] = 1
                queue.add(index)
            }
        }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val distance = distances[index]
            if (distance >= featherPixels) continue
            val x = index % width
            val y = index / width
            fun spread(next: Int) {
                if (selected[next] && distances[next] == 0) {
                    distances[next] = distance + 1
                    colors[next] = colors[index]
                    queue.add(next)
                }
            }
            if (x > 0) spread(index - 1)
            if (x + 1 < width) spread(index + 1)
            if (y > 0) spread(index - width)
            if (y + 1 < height) spread(index + width)
        }
        return colors
    }

    /** Interpolate premultiplied colors, then store straight-alpha ARGB. */
    fun blend(fill: Int, outside: Int, weight: Int): Int {
        val inverse = ALPHA_MAX - weight
        val fillAlpha = android.graphics.Color.alpha(fill)
        val outsideAlpha = android.graphics.Color.alpha(outside)
        val alpha = (fillAlpha * weight + outsideAlpha * inverse + 127) / ALPHA_MAX
        if (alpha == 0) return 0
        fun channel(fillChannel: Int, outsideChannel: Int): Int =
            (fillChannel * fillAlpha * weight + outsideChannel * outsideAlpha * inverse +
                alpha * ALPHA_MAX / 2) / (alpha * ALPHA_MAX)
        return android.graphics.Color.argb(
            alpha,
            channel(android.graphics.Color.red(fill), android.graphics.Color.red(outside)),
            channel(android.graphics.Color.green(fill), android.graphics.Color.green(outside)),
            channel(android.graphics.Color.blue(fill), android.graphics.Color.blue(outside))
        )
    }
    /** The inner feather never extends beyond the selected region. */
    fun previewBounds(regions: List<ImageFillRegion>): Rect {
        return bounds(regions)
    }

    private fun fillSmallEnclosedIslands(selected: BooleanArray, width: Int) {
        val height = selected.size / width
        val visited = BooleanArray(selected.size)
        for (start in selected.indices) {
            if (selected[start] || visited[start]) continue

            val queue = ArrayDeque<Int>()
            val island = mutableListOf<Int>()
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            var touchesCanvasEdge = false
            queue.add(start)
            visited[start] = true

            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                island += index
                val column = index % width
                val row = index / width
                left = minOf(left, column)
                top = minOf(top, row)
                right = maxOf(right, column)
                bottom = maxOf(bottom, row)
                touchesCanvasEdge = touchesCanvasEdge ||
                    column == 0 || row == 0 || column == width - 1 || row == height - 1

                enqueue(index - 1, column > 0, selected, visited, queue)
                enqueue(index + 1, column + 1 < width, selected, visited, queue)
                enqueue(index - width, row > 0, selected, visited, queue)
                enqueue(index + width, row + 1 < height, selected, visited, queue)
            }

            if (!touchesCanvasEdge &&
                right - left + 1 <= MAX_ENCLOSED_ISLAND_WIDTH &&
                bottom - top + 1 <= MAX_ENCLOSED_ISLAND_HEIGHT
            ) {
                island.forEach { selected[it] = true }
            }
        }
    }

    private fun enqueue(
        index: Int,
        isValid: Boolean,
        selected: BooleanArray,
        visited: BooleanArray,
        queue: ArrayDeque<Int>
    ) {
        if (isValid && !selected[index] && !visited[index]) {
            visited[index] = true
            queue.add(index)
        }
    }

    private fun insideDistances(selected: BooleanArray, width: Int, featherPixels: Int): IntArray {
        val distances = IntArray(selected.size) { -1 }
        val queue = ArrayDeque<Int>()
        selected.forEachIndexed { index, isSelected ->
            if (!isSelected) return@forEachIndexed
            val column = index % width
            val row = index / width
            if (hasUnselectedNeighbour(index, column, row, selected, width)) {
                distances[index] = 1
                queue.add(index)
            }
        }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            if (distances[index] >= featherPixels) continue
            val column = index % width
            val row = index / width
            val nextDistance = distances[index] + 1
            enqueueInsideDistance(index - 1, column > 0, selected, distances, queue, nextDistance)
            enqueueInsideDistance(index + 1, column + 1 < width, selected, distances, queue, nextDistance)
            enqueueInsideDistance(index - width, row > 0, selected, distances, queue, nextDistance)
            enqueueInsideDistance(index + width, row + 1 < selected.size / width, selected, distances, queue, nextDistance)
        }
        return distances
    }

    private fun hasUnselectedNeighbour(
        index: Int,
        column: Int,
        row: Int,
        selected: BooleanArray,
        width: Int
    ): Boolean {
        val height = selected.size / width
        return (column > 0 && !selected[index - 1]) ||
            (column + 1 < width && !selected[index + 1]) ||
            (row > 0 && !selected[index - width]) ||
            (row + 1 < height && !selected[index + width])
    }

    private fun enqueueInsideDistance(
        index: Int,
        isValid: Boolean,
        selected: BooleanArray,
        distances: IntArray,
        queue: ArrayDeque<Int>,
        distance: Int = 1
    ) {
        if (isValid && selected[index] && distances[index] < 0) {
            distances[index] = distance
            queue.add(index)
        }
    }

    private const val MAX_ENCLOSED_ISLAND_WIDTH = 10
    private const val MAX_ENCLOSED_ISLAND_HEIGHT = 10
    const val ALPHA_MAX = 255
    private const val IMAGE_FEATHER_PIXELS = 4
}
