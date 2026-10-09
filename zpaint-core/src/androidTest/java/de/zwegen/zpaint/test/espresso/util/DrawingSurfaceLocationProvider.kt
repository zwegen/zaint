package de.zwegen.zpaint.test.espresso.util

import android.graphics.PointF
import android.view.View
import androidx.test.espresso.action.CoordinatesProvider
import de.zwegen.zpaint.tools.implementation.ZaintShapeToolBase

enum class DrawingSurfaceLocationProvider : CoordinatesProvider {
    MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, .5f)
    },
    LEFT_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .0f, .5f)
    },
    HALFWAY_LEFT_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .25f, .5f)
    },
    RIGHT_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, 1f, .5f)
    },
    HALFWAY_RIGHT_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .75f, .5f)
    },
    TOP_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, 0f)
    },
    HALFWAY_TOP_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, .25f)
    },
    HALFWAY_BOTTOM_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, .75f)
    },
    HALFWAY_TOP_LEFT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .25f, .25f)
    },
    HALFWAY_TOP_RIGHT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .75f, .25f)
    },
    HALFWAY_BOTTOM_LEFT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .25f, .75f)
    },
    HALFWAY_BOTTOM_RIGHT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .75f, .75f)
    },
    BOTTOM_RIGHT_CLOSE_CENTER {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .55f, .55f)
    },
    BOTTOM_RIGHT_CORNER {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, 0.95f, .9f)
    },
    BOTTOM_MIDDLE {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, 0.9f)
    },
    OUTSIDE_MIDDLE_RIGHT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, 1.5f, .5f)
    },
    OUTSIDE_MIDDLE_LEFT {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, -.3f, .5f)
    },
    OUTSIDE_MIDDLE_BOTTOM {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, 1.3f, .5f)
    },
    OUTSIDE_MIDDLE_TOP {
        override fun calculateCoordinates(view: View): FloatArray = calculatePercentageOffset(view, .5f, -.3f)
    },
    TOOL_POSITION {
        override fun calculateCoordinates(view: View): FloatArray {
            val mainActivity = MainActivityHelper.getMainActivityFromView(view)
            val workspace = mainActivity.workspace
            val toolPosition =
                (mainActivity.toolReference.tool as ZaintShapeToolBase?)!!.toolPosition
            val point = workspace.getSurfacePointFromCanvasPoint(toolPosition)
            return PositionCoordinatesProvider.calculateViewOffset(view, point.x, point.y)
        }
    };

    companion object {
        private fun calculatePercentageOffset(
            view: View,
            percentageX: Float,
            percentageY: Float
        ): FloatArray {
            val mainActivity = MainActivityHelper.getMainActivityFromView(view)
            val workspace = mainActivity.workspace
            val pointX = workspace.width * percentageX
            val pointY = workspace.height * percentageY
            val point = workspace.getSurfacePointFromCanvasPoint(PointF(pointX, pointY))
            return PositionCoordinatesProvider.calculateViewOffset(view, point.x, point.y)
        }
    }
}
