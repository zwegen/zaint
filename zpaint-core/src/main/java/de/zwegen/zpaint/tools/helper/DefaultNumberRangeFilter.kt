package de.zwegen.zpaint.tools.helper

import android.text.Spanned
import de.zwegen.zpaint.ui.tools.NumberRangeFilter

/** Keeps a numeric option inside its configured inclusive range. */
class DefaultNumberRangeFilter(
    private val minimum: Int,
    override var max: Int
) : NumberRangeFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val proposedValue = buildProposedValue(source, start, end, dest, dstart, dend)
        if (proposedValue.isEmpty()) return null
        if (proposedValue == "-" && minimum < 0) return null

        val numericValue = proposedValue.toIntOrNull() ?: return ""
        return when {
            numericValue in minimum..max -> null
            numericValue >= 0 && canGrowIntoRange(numericValue) -> null
            else -> ""
        }
    }

    /**
     * Allows a short, temporarily too-small prefix while the user is entering a value.
     * For example, "1" must be possible on the way to "10" for a 10..100 range.
     */
    private fun canGrowIntoRange(value: Int): Boolean {
        if (value == 0) return false
        var smallest = value.toLong()
        var largest = smallest
        while (smallest <= max) {
            if (largest >= minimum) return true
            smallest *= 10
            largest = largest * 10 + 9
        }
        return false
    }

    private fun buildProposedValue(
        source: CharSequence,
        start: Int,
        end: Int,
        destination: Spanned,
        replaceStart: Int,
        replaceEnd: Int
    ): String = buildString {
        append(destination, 0, replaceStart)
        append(source, start, end)
        append(destination, replaceEnd, destination.length)
    }
}
