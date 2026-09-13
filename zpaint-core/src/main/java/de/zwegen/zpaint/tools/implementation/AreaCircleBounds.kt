/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.PointF
import kotlin.math.hypot

object AreaCircleBounds {
    fun keepIntersectingCanvas(center: PointF, radius: Float, width: Int, height: Int) {
        center.x = center.x.coerceIn(-radius, width.toFloat() + radius)
        center.y = center.y.coerceIn(-radius, height.toFloat() + radius)
    }

    fun maxRadiusForCanvas(width: Int, height: Int, minRadius: Float): Float =
        hypot(width.toDouble(), height.toDouble()).toFloat()
            .coerceAtLeast(minRadius)
}
