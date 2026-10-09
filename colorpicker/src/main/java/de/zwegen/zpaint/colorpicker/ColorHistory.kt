package de.zwegen.zpaint.colorpicker

import java.io.Serializable

/** The five most recently applied colors, with optional session-only image suggestions. */
class ColorHistory : Serializable {
    private val entries = arrayListOf<Int>()

    @Transient
    private var imageEntries: ArrayList<Int>? = null

    @Transient
    var needsImageColors: Boolean = false
        private set

    val colors: ArrayList<Int>
        get() = ArrayList(imageEntries ?: entries)

    /** Image suggestions are never written into a project or recovery file. */
    val storedColors: ArrayList<Int>
        get() = ArrayList(entries)

    fun addColor(color: Int) {
        update(entries, color)
        imageEntries?.let { update(it, color) }
    }

    fun requestImageColors() {
        imageEntries = null
        needsImageColors = true
    }

    fun seedImageColors(colors: List<Int>) {
        if (!needsImageColors) return
        needsImageColors = false
        imageEntries = colors.distinct().take(MAX_ENTRIES).takeIf { it.isNotEmpty() }?.let(::ArrayList)
    }

    fun clearImageColors() {
        imageEntries = null
        needsImageColors = false
    }

    private fun update(colors: ArrayList<Int>, color: Int) {
        colors.remove(color)
        colors.add(color)
        if (colors.size > MAX_ENTRIES) colors.removeAt(0)
    }

    companion object {
        const val MAX_ENTRIES = 5
    }
}
