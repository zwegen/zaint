package de.zwegen.zpaint.test.utils

import android.graphics.PointF
import org.mockito.kotlin.argThat
import org.mockito.ArgumentMatcher

class PointFMatcher(private val pointX: Float, private val pointY: Float) : ArgumentMatcher<PointF> {
    override fun matches(argument: PointF): Boolean = argument.x == pointX && argument.y == pointY

    override fun toString(): String = "PointF($pointX, $pointY)"

    companion object {
        fun pointFEquals(x: Float, y: Float) = argThat(PointFMatcher(x, y))
    }
}
