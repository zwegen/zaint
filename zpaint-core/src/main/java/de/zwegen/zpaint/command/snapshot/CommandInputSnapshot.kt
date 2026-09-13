package de.zwegen.zpaint.command.snapshot

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import de.zwegen.zpaint.command.serialization.ZaintStrokePath

/**
 * Creates the immutable-at-creation copies that undo commands need.
 *
 * Tool previews remain editable after a command was created. Commands therefore receive their own
 * paint, coordinates, paths and bitmap input instead of retaining those live tool objects.
 */
class CommandInputSnapshot {
    fun paint(source: Paint): Paint = Paint(source)

    fun point(source: PointF): PointF = PointF(source.x, source.y)

    fun point(x: Int, y: Int): Point = Point(x, y)

    fun pixelPoint(source: PointF): Point = Point(source.x.toInt(), source.y.toInt())

    fun path(source: ZaintStrokePath): ZaintStrokePath = ZaintStrokePath(source)

    fun points(source: List<PointF>): MutableList<PointF> = source.mapTo(mutableListOf(), ::point)

    fun bitmap(source: Bitmap): Bitmap = source.copy(Bitmap.Config.ARGB_8888, false)
}
