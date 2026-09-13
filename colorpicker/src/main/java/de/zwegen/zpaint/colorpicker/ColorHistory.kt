package de.zwegen.zpaint.colorpicker

import java.io.Serializable

/** The four most recently applied colors, kept in application state. */
class ColorHistory : Serializable {
    private val entries = arrayListOf<Int>()

    val colors: ArrayList<Int>
        get() = ArrayList(entries)

    fun addColor(color: Int) {
        entries.remove(color)
        entries.add(color)
        if (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    private companion object {
        const val MAX_ENTRIES = 4
    }
}
