package de.zwegen.zpaint.command.implementation

import kotlin.math.hypot

/** Android-free deformation math, kept separate so the stroke behavior can be unit tested. */
data class WarpPoint(val x: Float, val y: Float)

object WarpStrokeMath {
    fun inverseWarp(
        x: Float,
        y: Float,
        points: List<WarpPoint>,
        radius: Float,
        strength: Float
    ): WarpPoint {
        var sampleX = x
        var sampleY = y
        for (index in points.lastIndex downTo 1) {
            val start = points[index - 1]
            val end = points[index]
            val deltaX = end.x - start.x
            val deltaY = end.y - start.y
            val distance = hypot(sampleX - end.x, sampleY - end.y)
            if (distance >= radius) continue
            val falloff = (1f - distance / radius).let { it * it }
            sampleX -= deltaX * falloff * strength
            sampleY -= deltaY * falloff * strength
        }
        return WarpPoint(sampleX, sampleY)
    }
}
