package de.zwegen.zpaint.tools.implementation

import android.graphics.PointF

/** Coordinates and completion flags for one line-tool interaction. */
class LineInputState {
    var isFinalized = false
    var isStartSet = false
    var isEndSet = false
    var initialCoordinate: PointF? = null
    var startPoint: PointF? = null
    var endPoint: PointF? = null
    var currentCoordinate: PointF? = null

    fun beginDrag(coordinate: PointF) {
        initialCoordinate = coordinate.copyPoint()
    }

    fun showPreview(coordinate: PointF) {
        currentCoordinate = coordinate.copyPoint()
    }

    fun continueFromStart(): PointF? = startPoint?.copyPoint()?.also {
        initialCoordinate = it.copyPoint()
    }

    fun setStart(previous: PointF?, xDistance: Float, yDistance: Float): PointF? {
        return previous?.offsetBy(xDistance, yDistance)?.also {
            startPoint = it
            isStartSet = true
        }
    }

    fun setEnd(previous: PointF?, xDistance: Float, yDistance: Float): PointF? {
        return previous?.offsetBy(xDistance, yDistance)?.also {
            endPoint = it
            isEndSet = true
        }
    }

    fun beginConnectedSegment(): PointF? {
        val nextStart = endPoint?.copyPoint() ?: return null
        initialCoordinate = nextStart.copyPoint()
        startPoint = null
        endPoint = null
        isStartSet = false
        isEndSet = false
        isFinalized = false
        return nextStart
    }

    fun clearTransientCoordinates() {
        initialCoordinate = null
        currentCoordinate = null
    }

    fun clearFinalizedLine() {
        startPoint = null
        endPoint = null
        isStartSet = false
        isEndSet = false
        isFinalized = false
    }

    private fun PointF.offsetBy(xDistance: Float, yDistance: Float): PointF =
        PointF(x - xDistance, y - yDistance)

    private fun PointF.copyPoint(): PointF = PointF(x, y)
}
