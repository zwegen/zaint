package de.zwegen.zpaint.test.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.implementation.ZaintDocumentReset
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ZaintDocumentResetTest {
    @Mock
    var canvas: Canvas? = null

    @InjectMocks
    var command: ZaintDocumentReset? = null
    private var layerModel: ZaintLayerContracts.Model? = null

    @Before
    fun setUp() { layerModel = ZaintLayerModel() }

    @Test
    fun testRunClearsLayers() {
        layerModel?.addLayerAt(0, ZaintLayer(mock(Bitmap::class.java)))
        layerModel?.addLayerAt(1, ZaintLayer(mock(Bitmap::class.java)))
        canvas?.let { layerModel?.let { it1 -> command?.run(it, it1) } }
        Assert.assertThat(layerModel?.layerCount, CoreMatchers.`is`(0))
    }
}
