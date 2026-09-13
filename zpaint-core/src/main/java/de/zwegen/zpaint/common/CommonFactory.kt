package de.zwegen.zpaint.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import de.zwegen.zpaint.command.serialization.ZaintStrokePath

/**
 * Creates mutable graphics values at the command boundary.
 *
 * Keeping object construction here makes command tests independent from Android's
 * static constructors and ensures that mutable input values are copied instead of
 * being shared with an undoable command.
 */
open class CommonFactory {
    open fun createCanvas(): Canvas = Canvas()

    open fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }

    fun createPaint(source: Paint?): Paint {
        return if (source == null) Paint() else Paint(source)
    }

    fun createPointF(source: PointF): PointF {
        return PointF(source.x, source.y)
    }

    fun createPoint(x: Int, y: Int): Point = Point(x, y)

    open fun createSerializablePath(source: ZaintStrokePath): ZaintStrokePath {
        return ZaintStrokePath(source)
    }

    fun createRectF(source: RectF?): RectF {
        return if (source == null) RectF() else RectF(source)
    }
}
