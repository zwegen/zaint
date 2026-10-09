package de.zwegen.zpaint.test.espresso.util

import android.os.SystemClock
import androidx.test.espresso.UiController
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.Swiper

enum class CustomSwiper : Swiper {
    ACCURATE {
        override fun sendSwipe(uiController: UiController, startCoordinates: FloatArray, endCoordinates: FloatArray, precision: FloatArray): Swiper.Status {
            val steps = interpolate(startCoordinates, endCoordinates, STEPS)
            val delayBetweenMovements = DURATION / steps.size
            val downEvent = MotionEvents.sendDown(uiController, startCoordinates, precision).down
            try {
                for (i in steps.indices) {
                    MotionEvents.sendMovement(uiController, downEvent, steps[i])
                    val desiredTime = downEvent.downTime + delayBetweenMovements * i
                    val timeUntilDesired = desiredTime - SystemClock.uptimeMillis()
                    if (timeUntilDesired > 10) {
                        uiController.loopMainThreadForAtLeast(timeUntilDesired)
                    }
                }
                MotionEvents.sendUp(uiController, downEvent, endCoordinates)
            } finally {
                downEvent.recycle()
            }
            return Swiper.Status.SUCCESS
        }
    };

    companion object {
        private const val STEPS = 10
        private const val DURATION = 500
        private fun interpolate(start: FloatArray, end: FloatArray, steps: Int): Array<FloatArray> {
            val res = Array(steps) { FloatArray(2) }
            res[0][0] = start[0]
            res[0][1] = start[1]
            for (i in 2 until steps) {
                res[i - 1][0] = start[0] + (end[0] - start[0]) * i / (steps + 2f)
                res[i - 1][1] = start[1] + (end[1] - start[1]) * i / (steps + 2f)
            }
            res[steps - 1][0] = end[0]
            res[steps - 1][1] = end[1]
            return res
        }
    }
}
