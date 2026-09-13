package de.zwegen.zpaint.tools.implementation

import android.graphics.PointF
import de.zwegen.zpaint.command.serialization.ZaintStrokePath

/** Immutable line endpoints shared by preview, undo replay and final drawing. */
data class LineSegment(val start: PointF?, val end: PointF?) {
    fun toPath(): ZaintStrokePath = ZaintStrokePath().apply {
        if (start != null && end != null) {
            moveTo(start.x, start.y)
            lineTo(end.x, end.y)
        }
    }

    fun isComplete(): Boolean = start != null && end != null
}
