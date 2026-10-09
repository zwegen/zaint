package de.zwegen.zpaint.test.espresso.util

import android.view.View
import androidx.test.espresso.action.CoordinatesProvider

class OffsetLocationProvider(private val locationProvider: CoordinatesProvider, private val xOffset: Int, private val yOffset: Int) : CoordinatesProvider {
    override fun calculateCoordinates(view: View): FloatArray {
        val coordinates = locationProvider.calculateCoordinates(view)
        coordinates[0] += xOffset.toFloat()
        coordinates[1] += yOffset.toFloat()
        return coordinates
    }

    companion object {
        @JvmStatic
        fun withOffset(locationProvider: CoordinatesProvider, xOffset: Int, yOffset: Int): CoordinatesProvider = OffsetLocationProvider(locationProvider, xOffset, yOffset)
    }
}
