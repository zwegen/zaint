package de.zwegen.zpaint.tools.options

/**
 * Presentation boundary for Zaint's smudge controls.
 *
 * Brush shape and size use [ZaintBrushOptions]; this boundary adds the
 * smudge-specific pressure and drag values without giving the panel state of
 * the tool itself.
 */
interface ZaintSmudgeOptions : ZaintBrushOptions {
    fun setSmudgeListener(listener: Listener)

    interface Listener {
        fun onPressurePercentSelected(percent: Int)
        fun onDragPercentSelected(percent: Int)
    }
}
