package de.zwegen.zpaint.colorpicker

/** Bounded, deterministic color quantization; does not need Android or external services. */
object ImageColorPalette {
    private const val BIN_COUNT = 4096
    private const val MIN_DISTANCE_SQUARED = 80 * 80

    fun extract(pixels: IntArray, maxColors: Int = ColorHistory.MAX_ENTRIES): List<Int> {
        if (maxColors <= 0) return emptyList()
        val counts = IntArray(BIN_COUNT)
        val red = LongArray(BIN_COUNT)
        val green = LongArray(BIN_COUNT)
        val blue = LongArray(BIN_COUNT)
        for (pixel in pixels) {
            if ((pixel ushr 24) < 128) continue
            val r = (pixel ushr 16) and 255
            val g = (pixel ushr 8) and 255
            val b = pixel and 255
            val bin = ((r ushr 4) shl 8) or ((g ushr 4) shl 4) or (b ushr 4)
            counts[bin]++
            red[bin] += r.toLong()
            green[bin] += g.toLong()
            blue[bin] += b.toLong()
        }
        val colors = ArrayList<Int>(maxColors)
        val candidates = counts.indices.filter { counts[it] > 0 }.sortedWith(
            compareByDescending<Int> { counts[it] }.thenBy { it }
        ).map { bin ->
            val r = (red[bin] / counts[bin]).toInt()
            val g = (green[bin] / counts[bin]).toInt()
            val b = (blue[bin] / counts[bin]).toInt()
            Candidate((255 shl 24) or (r shl 16) or (g shl 8) or b, counts[bin])
        }.toMutableList()
        if (candidates.isEmpty()) return emptyList()

        // Start with the dominant color, then balance separation against population.
        colors.add(candidates.removeAt(0).color)
        while (colors.size < maxColors && candidates.isNotEmpty()) {
            var bestIndex = -1
            var bestScore = -1.0
            for ((index, candidate) in candidates.withIndex()) {
                val distance = colors.minOf { distanceSquared(it, candidate.color) }
                if (distance < MIN_DISTANCE_SQUARED) continue
                val score = distance * kotlin.math.sqrt(candidate.population.toDouble())
                if (score > bestScore) {
                    bestIndex = index
                    bestScore = score
                }
            }
            if (bestIndex < 0) break // Do not fill remaining slots with near-duplicates.
            colors.add(candidates.removeAt(bestIndex).color)
        }
        return colors
    }

    private data class Candidate(val color: Int, val population: Int)

    private fun distanceSquared(first: Int, second: Int): Int {
        val r = ((first ushr 16) and 255) - ((second ushr 16) and 255)
        val g = ((first ushr 8) and 255) - ((second ushr 8) and 255)
        val b = (first and 255) - (second and 255)
        return r * r + g * g + b * b
    }
}
