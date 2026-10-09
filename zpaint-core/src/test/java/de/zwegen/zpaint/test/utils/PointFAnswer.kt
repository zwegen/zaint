package de.zwegen.zpaint.test.utils

import android.graphics.PointF
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.stubbing.Stubber

class PointFAnswer(private val pointX: Float, private val pointY: Float) :
    Answer<Any?> {
    override fun answer(invocation: InvocationOnMock): Any? {
        val point = invocation.getArgument<PointF>(0)
        point.x = pointX
        point.y = pointY
        return null
    }

    companion object {
        fun setPointFTo(x: Float, y: Float): Stubber = Mockito.doAnswer(PointFAnswer(x, y))
    }
}
