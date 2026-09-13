/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.options

interface PixelToolOptionsView {
    fun setCallback(callback: Callback)

    fun setPixelSizeRange(min: Int, max: Int, value: Int)

    fun setAreaMode(areaMode: Boolean)

    interface Callback {
        fun setAreaMode(areaMode: Boolean)

        fun setPixelSize(pixelSize: Int)

        fun applyPixelClicked()
    }
}
