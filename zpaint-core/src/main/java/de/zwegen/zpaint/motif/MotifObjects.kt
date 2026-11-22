package de.zwegen.zpaint.motif

/** Immutable selection at model resolution; removing one object preserves overlapping objects. */
internal class MotifObjects private constructor(private val entries: List<Entry>) {
    constructor() : this(emptyList())
    private data class Entry(val anchor: Int, val matte: ByteArray)

    fun toggle(point: Int, infer: () -> ByteArray): MotifObjects {
        val hit = entries.indexOfLast { it.anchor == point }.takeIf { it >= 0 }
            ?: entries.indices.reversed().maxByOrNull { entries[it].matte[point].toInt() and 255 }
                ?.takeIf { (entries[it].matte[point].toInt() and 255) >= 128 }
        if (hit != null) return MotifObjects(entries.filterIndexed { index, _ -> index != hit })

        val matte = infer()
        // A failed/empty prediction must not turn the inverse target into a whole-image effect.
        if (matte.none { (it.toInt() and 255) >= 128 }) return this
        return MotifObjects(entries + Entry(point, matte))
    }

    fun union(): ByteArray? {
        val first = entries.firstOrNull() ?: return null
        return first.matte.copyOf().also { result ->
            for (i in 1 until entries.size) CutoutUnion.include(result, entries[i].matte)
        }
    }
}
