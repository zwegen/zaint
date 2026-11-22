/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.options

import androidx.annotation.StringRes

enum class FilterRegion { TOTAL, AREA, AUTO }

interface FilterToolOptionsView {
    fun setCallback(callback: Callback)

    fun setTitle(titleResource: Int)

    fun setRange(min: Int, max: Int, value: Int)

    fun setAreaModeVisible(visible: Boolean)

    fun setRegion(region: FilterRegion)

    fun setAutoHint(@StringRes hint: Int)

    /** Shows the Splash colour slots and marks the slot currently used for colour picking. */
    fun setSplashColors(colors: List<Int>, activeIndex: Int)

    fun setSplashColorListener(listener: SplashColorListener)

    interface Callback {
        fun onValueChanged(value: Int)

        fun onStartTracking()

        fun onStopTracking(value: Int)

        fun setRegion(region: FilterRegion)
    }

    interface SplashColorListener {
        fun onAddSplashColor()

        fun onSplashColorSelected(index: Int)

        fun onRemoveSplashColor(index: Int)
    }
}
