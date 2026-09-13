/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.options

interface RotateToolOptionsView {
    fun setCallback(callback: Callback)

    fun setAngle(angle: Int)

    interface Callback {
        fun onAngleChanged(angle: Int)

        fun onStartTracking()

        fun onStopTracking(angle: Int)

        fun rotateCounterClockwiseClicked()

        fun rotateClockwiseClicked()

        fun flipHorizontalClicked()

        fun flipVerticalClicked()
    }
}
