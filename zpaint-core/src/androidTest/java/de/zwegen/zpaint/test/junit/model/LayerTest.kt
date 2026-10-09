package de.zwegen.zpaint.test.junit.model

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.ZaintCommandTimeline.CommandListener
import de.zwegen.zpaint.command.implementation.ZaintHistoryDispatcher
import de.zwegen.zpaint.command.implementation.ZaintCommandFactory
import de.zwegen.zpaint.command.implementation.ZaintHistoryManager
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
class LayerTest {
    private var commandFactory: ZaintCommandFactoryApi? = null
    private var commandManager: ZaintCommandTimeline? = null
    private var layerModel: ZaintLayerContracts.Model? = null
    private val layerHeight = 200
    private val layerWidth = 200
    @Before
    fun setUp() {
        commandFactory = ZaintCommandFactory()
        layerModel = ZaintLayerModel()
        (layerModel as ZaintLayerModel).width = layerWidth
        (layerModel as ZaintLayerModel).height = layerHeight
        val layer = ZaintLayer(Bitmap.createBitmap(layerWidth, layerHeight, Bitmap.Config.ARGB_8888))
        (layerModel as ZaintLayerModel).addLayerAt(0, layer)
        (layerModel as ZaintLayerModel).currentLayer = layer
        commandManager =
            ZaintHistoryDispatcher(ZaintHistoryManager(CommonFactory(), layerModel as ZaintLayerModel),
                                layerModel as ZaintLayerModel
            )
    }

    @Test
    fun testCreateManyLayers() {
        for (i in 0..9) {
            commandManager!!.addCommand(commandFactory!!.createAddEmptyLayerCommand())
            commandManager!!.addCommand(commandFactory!!.createRemoveLayerCommand(1))
        }
    }

    @Test
    fun testMoveLayer() {
        val listener = Mockito.mock(CommandListener::class.java)
        commandManager!!.addCommandListener(listener)
        commandManager!!.addCommand(commandFactory!!.createAddEmptyLayerCommand())
        Mockito.verify(listener, Mockito.timeout(1000)).commandPostExecute()
        Assert.assertThat(layerModel!!.layerCount, Matchers.`is`(2))
        val firstLayer = layerModel!!.getLayerAt(0)
        val secondLayer = layerModel!!.getLayerAt(1)
        Mockito.reset(listener)
        commandManager!!.addCommand(commandFactory!!.createReorderLayersCommand(0, 1))
        Mockito.verify(listener, Mockito.timeout(1000)).commandPostExecute()
        Assert.assertThat(layerModel!!.layerCount, Matchers.`is`(2))
        Assert.assertThat(
            layerModel!!.getLayerAt(0), Matchers.`is`(secondLayer)
        )
        Assert.assertThat(
            layerModel!!.getLayerAt(1), Matchers.`is`(firstLayer)
        )
    }

    @Test
    fun testMergeLayers() {
        val listener = Mockito.mock(CommandListener::class.java)
        val firstLayer = layerModel!!.getLayerAt(0)
        firstLayer!!.bitmap.setPixel(1, 1, Color.BLACK)
        firstLayer.bitmap.setPixel(1, 2, Color.BLACK)
        commandManager!!.addCommandListener(listener)
        commandManager!!.addCommand(commandFactory!!.createAddEmptyLayerCommand())
        Mockito.verify(listener, Mockito.timeout(1000)).commandPostExecute()
        val secondLayer = layerModel!!.getLayerAt(0)
        Assert.assertThat(
            layerModel!!.currentLayer, Matchers.`is`(secondLayer)
        )
        secondLayer!!.bitmap.setPixel(1, 1, Color.BLUE)
        secondLayer.bitmap.setPixel(2, 1, Color.BLUE)
        Mockito.reset(listener)
        commandManager!!.addCommand(commandFactory!!.createMergeLayersCommand(0, 1))
        Mockito.verify(listener, Mockito.timeout(1000)).commandPostExecute()
        Assert.assertThat(layerModel!!.layerCount, Matchers.`is`(1))
        Assert.assertThat(
            layerModel!!.currentLayer, Matchers.`is`(firstLayer)
        )
        Assert.assertThat(
            layerModel!!.getLayerAt(0), Matchers.`is`(firstLayer)
        )
        Assert.assertThat(firstLayer.bitmap.getPixel(1, 2), Matchers.`is`(Color.BLACK))
        Assert.assertThat(firstLayer.bitmap.getPixel(2, 1), Matchers.`is`(Color.BLUE))
        Assert.assertThat(firstLayer.bitmap.getPixel(1, 1), Matchers.`is`(Color.BLUE))
    }

    @Test
    fun testGetLayerAt() {
        for (i in 0 until layerModel!!.layerCount) {
            Assert.assertNotNull(layerModel!!.getLayerAt(i))
        }
        Assert.assertNull(layerModel!!.getLayerAt(-1))
        Assert.assertNull(layerModel!!.getLayerAt(layerModel!!.layerCount))
    }

    @Test
    fun testAddLayerAt() {
        val layer = ZaintLayer(Bitmap.createBitmap(layerWidth, layerHeight, Bitmap.Config.ARGB_8888))
        Assert.assertTrue(layerModel!!.addLayerAt(0, layer))
        Assert.assertTrue(layerModel!!.addLayerAt(layerModel!!.layerCount, layer))
        Assert.assertFalse(layerModel!!.addLayerAt(-1, layer))
        Assert.assertFalse(layerModel!!.addLayerAt(layerModel!!.layerCount + 1, layer))
    }

    @Test
    fun testRemoveLayerAt() {
        Assert.assertFalse(layerModel!!.removeLayerAt(-1))
        Assert.assertTrue(layerModel!!.removeLayerAt(0))
        val layer = ZaintLayer(Bitmap.createBitmap(layerWidth, layerHeight, Bitmap.Config.ARGB_8888))
        layerModel!!.addLayerAt(0, layer)
        Assert.assertFalse(layerModel!!.removeLayerAt(layerModel!!.layerCount))
    }
}
