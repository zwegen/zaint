package de.zwegen.zpaint.test.command.implementation

import android.graphics.Canvas
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.command.implementation.ZaintDocumentDimensions
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test

class ZaintDocumentDimensionsTest {
    @Test
    fun testRun() {
        val layerModel = ZaintLayerModel()
        val command = ZaintDocumentDimensions(3, 4)
        command.run(Canvas(), layerModel)

        Assert.assertThat(layerModel.width, CoreMatchers.`is`(3))
        Assert.assertThat(layerModel.height, CoreMatchers.`is`(4))
    }
}
