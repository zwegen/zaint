package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import de.zwegen.zpaint.command.implementation.ZaintPointCommand
import de.zwegen.zpaint.model.ZaintLayerModel
import org.junit.Test
import org.mockito.Mockito

class PointCommandTest {
    @Test
    fun drawsOneCapturedPoint() {
        val canvas = Mockito.mock(Canvas::class.java)
        val command = ZaintPointCommand(Paint(), PointF(3f, 7f))

        command.run(canvas, ZaintLayerModel())

        Mockito.verify(canvas).drawPoint(
            Mockito.anyFloat(),
            Mockito.anyFloat(),
            Mockito.any(Paint::class.java)
        )
    }
}
