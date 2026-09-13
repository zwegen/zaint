package de.zwegen.zpaint.tools

import android.graphics.PointF

/** Optional contract for a transient tool preview that owns two-finger gestures. */
interface TwoFingerTransformTool {
    fun beginTwoFingerTransform(first: PointF, second: PointF): Boolean
    fun updateTwoFingerTransform(first: PointF, second: PointF)
    fun endTwoFingerTransform()
}
