package de.zwegen.zpaint.tools.options

interface ReplaceToolOptionsView {
    fun setCallback(callback: Callback)

    fun setBrushSizeRange(minimum: Int, maximum: Int, value: Int)

    interface Callback {
        fun setBrushSize(size: Int)
    }
}
