package de.zwegen.zpaint.test.junit.stubs

import android.graphics.Path
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import org.mockito.Mockito

class PathStub : ZaintStrokePath() {

    val stub: ZaintStrokePath = Mockito.mock(ZaintStrokePath::class.java)

    fun getStub(): Path = stub

    override fun reset() {
        stub.reset()
    }

    override fun rewind() {
        stub.rewind()
    }

    override fun moveTo(x: Float, y: Float) {
        stub.moveTo(x, y)
    }

    override fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        stub.quadTo(x1, y1, x2, y2)
    }

    override fun lineTo(x: Float, y: Float) {
        stub.lineTo(x, y)
    }
}
