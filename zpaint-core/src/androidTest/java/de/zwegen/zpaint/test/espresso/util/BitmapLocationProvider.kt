package de.zwegen.zpaint.test.espresso.util

import android.view.View
import androidx.test.espresso.action.CoordinatesProvider
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.util.MainActivityHelper.Companion.getMainActivityFromView

enum class BitmapLocationProvider : CoordinatesProvider {
    MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, .5f)
    },
    MIDDLE_RIGHT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, 1f, .5f)
    },
    OUTSIDE_MIDDLE_RIGHT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, 1.5f, .5f)
    },
    HALFWAY_RIGHT_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .75f, .5f)
    },
    HALFWAY_LEFT_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .25f, .5f)
    },
    HALFWAY_BOTTOM_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, .75f)
                          },
    HALFWAY_TOP_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, .25f)
    },
    HALFWAY_TOP_LEFT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .25f, .25f)
    },
    HALFWAY_BOTTOM_RIGHT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .75f, .75f)
    },
    HALFWAY_BOTTOM_LEFT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .25f, .75f)
    };

    companion object {
        private fun calculatePercentageOffset(
            view: View,
            percentageX: Float,
            percentageY: Float
        ): FloatArray {
            val mainActivity: ZaintEditorActivity = getMainActivityFromView(view)
            val workspace = mainActivity.workspace
            val pointX = (workspace.width - 1) * percentageX
            val pointY = workspace.height * percentageY
            return floatArrayOf(pointX, pointY)
        }
    }
}
