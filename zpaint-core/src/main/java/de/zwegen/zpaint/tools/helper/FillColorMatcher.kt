package de.zwegen.zpaint.tools.helper

/** Decides whether a pixel belongs to the color region selected for a fill operation. */
class FillColorMatcher(
    private val referenceColor: Int,
    tolerance: Float
) {
    private val toleranceSquared = tolerance.toInt().let { it * it }
    private val acceptsNearbyColors = tolerance > 0f

    fun matches(pixel: Int): Boolean {
        if (pixel == referenceColor) {
            return true
        }
        if (!acceptsNearbyColors) {
            return false
        }

        return channelDistanceSquared(pixel, referenceColor) <= toleranceSquared
    }

    private fun channelDistanceSquared(first: Int, second: Int): Int =
        square(channel(first, 16) - channel(second, 16)) +
            square(channel(first, 8) - channel(second, 8)) +
            square(channel(first, 0) - channel(second, 0)) +
            square(channel(first, 24) - channel(second, 24))

    private fun channel(color: Int, shift: Int): Int = color ushr shift and 0xFF

    private fun square(value: Int): Int = value * value
}
