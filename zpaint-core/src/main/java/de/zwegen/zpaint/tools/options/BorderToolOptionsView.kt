/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.options

interface BorderToolOptionsView {
    fun setCallback(callback: Callback)

    fun setBorderAmount(amount: Int)

    interface Callback {
        fun onBorderAmountChanged(amount: Int)

        fun onStartTracking()

        fun onStopTracking(amount: Int)
    }
}
