package de.zwegen.zpaint.test.espresso.util

import android.view.View
import androidx.test.espresso.action.CoordinatesProvider

class PositionCoordinatesProvider(private val xCoordinate: Float, private val yCoordinate: Float) : CoordinatesProvider {
    override fun calculateCoordinates(view: View): FloatArray = calculateViewOffset(view, xCoordinate, yCoordinate)

    companion object {
        @JvmStatic
        fun at(x: Float, y: Float): CoordinatesProvider = PositionCoordinatesProvider(x, y)
        @JvmStatic
        fun calculateViewOffset(view: View, x: Float, y: Float): FloatArray {
            val screenLocation = IntArray(2)
            view.getLocationOnScreen(screenLocation)
            val touchX = screenLocation[0] + x
            val touchY = screenLocation[1] + y
            return floatArrayOf(touchX, touchY)
        }
    }
}
