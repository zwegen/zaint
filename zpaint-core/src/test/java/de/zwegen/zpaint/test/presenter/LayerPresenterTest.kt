package de.zwegen.zpaint.test.presenter

import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.presenter.ZaintLayerPresenter
import de.zwegen.zpaint.ui.dragndrop.LayerDragHandler
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LayerPresenterTest {
    @Mock
    private val layerDragHandler: LayerDragHandler? = null
    private val listItemDragHandler: LayerDragHandler?
        get() = layerDragHandler

    @Mock
    private val layerMenuViewHolder: ZaintLayerContracts.LayerMenuViewHolder? = null

    @Mock
    private val layerAdapter: ZaintLayerContracts.Adapter? = null

    @Mock
    private val navigator: ZaintLayerContracts.Navigator? = null

    @Mock
    private val commandManager: ZaintCommandTimeline? = null

    @Mock
    private val commandFactory: ZaintCommandFactoryApi? = null
    private var layerModel: ZaintLayerModel? = null
    private var layerPresenter: ZaintLayerPresenter? = null
    @Before
    fun setUp() { layerModel = ZaintLayerModel() }

    @Suppress("NestedBlockDepth")
    private fun createPresenter() {
        layerPresenter = layerModel?.let {
            layerMenuViewHolder?.let { it1 ->
                commandManager?.let { it2 ->
                    commandFactory?.let { it3 ->
                        navigator?.let { it4 ->
                            listItemDragHandler?.let { it5 ->
                                ZaintLayerPresenter(
                                    it, it5,
                                    it1, it2, it3, it4
                                )
                            }
                        }
                    }
                }
            }
        }
        if (layerAdapter != null) {
            layerPresenter?.setAdapter(layerAdapter)
        }
    }

    @Test
    fun testSetUp() {
        createPresenter()
        Mockito.verifyNoInteractions(
            layerAdapter, layerMenuViewHolder, listItemDragHandler,
            commandManager, commandFactory, navigator
        )
    }

    @Test
    fun testRefreshLayerMenuViewHolder() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        layerPresenter?.refreshLayerMenuViewHolder()
        Mockito.verify(layerMenuViewHolder)?.enableAddLayerButton()
        Mockito.verify(layerMenuViewHolder)?.enableDuplicateLayerButton()
        Mockito.verify(layerMenuViewHolder)?.enableRemoveLayerButton()
        Mockito.verify(layerMenuViewHolder)?.disableMergeLayerButton()
        Mockito.verifyNoMoreInteractions(layerMenuViewHolder)
        Mockito.verifyNoInteractions(
            commandManager,
            commandFactory,
            layerAdapter,
            listItemDragHandler
        )
    }

    @Test
    fun testRefreshLayerMenuViewHolderOnAddDisabled() {
        for (i in 0..99) {
            layerModel?.addLayerAt(i, Mockito.mock(ZaintLayer::class.java))
        }
        createPresenter()
        layerPresenter?.refreshLayerMenuViewHolder()
        Mockito.verify(layerMenuViewHolder)?.disableAddLayerButton()
        Mockito.verify(layerMenuViewHolder)?.disableDuplicateLayerButton()
        Mockito.verify(layerMenuViewHolder)?.enableRemoveLayerButton()
        Mockito.verify(layerMenuViewHolder)?.disableMergeLayerButton()
        Mockito.verifyNoMoreInteractions(layerMenuViewHolder)
        Mockito.verifyNoInteractions(
            commandManager,
            commandFactory,
            layerAdapter,
            listItemDragHandler
        )
    }

    @Test
    fun testRefreshLayerMenuViewHolderOnRemoveDisabled() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        layerPresenter?.refreshLayerMenuViewHolder()
        Mockito.verify(layerMenuViewHolder)?.enableAddLayerButton()
        Mockito.verify(layerMenuViewHolder)?.enableDuplicateLayerButton()
        Mockito.verify(layerMenuViewHolder)?.disableRemoveLayerButton()
        Mockito.verify(layerMenuViewHolder)?.disableMergeLayerButton()
        Mockito.verifyNoMoreInteractions(layerMenuViewHolder)
        Mockito.verifyNoInteractions(
            commandManager,
            commandFactory,
            layerAdapter,
            listItemDragHandler
        )
    }

    @Test
    fun testGetLayerCount() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(2, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(3, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        val result = layerPresenter?.layerCount
        Assert.assertEquals(4, result)
    }

    @Test
    fun testGetLayerItem() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(2, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(3, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        val result = layerPresenter?.getLayerItem(3)
        Assert.assertEquals(layerModel?.getLayerAt(3), result)
    }

    @Test
    fun testSelectBottomLayer() {
        val topLayer = Mockito.mock(ZaintLayer::class.java)
        val bottomLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, topLayer)
        layerModel?.addLayerAt(1, bottomLayer)
        layerModel?.currentLayer = topLayer
        createPresenter()

        layerPresenter?.selectBottomLayer()

        Assert.assertSame(bottomLayer, layerModel?.currentLayer)
        Mockito.verify(layerAdapter)?.notifyDataSetChanged()
        Mockito.verifyNoInteractions(commandManager)
    }

    @Test
    fun testGetLayerItemId() {
        createPresenter()
        Assert.assertEquals(3, layerPresenter?.getLayerItemId(3)?.toInt())
        Assert.assertEquals(0, layerPresenter?.getLayerItemId(0)?.toInt())
    }

    @Test
    fun testAddLayer() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createAddEmptyLayerCommand()).thenReturn(command)
        createPresenter()
        layerPresenter?.addLayer()
        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verifyNoInteractions(layerMenuViewHolder, layerAdapter, listItemDragHandler)
    }

    @Test
    fun testAddLayerWhenTooManyLayers() {
        for (i in 0..99) {
            layerModel?.addLayerAt(i, Mockito.mock(ZaintLayer::class.java))
        }
        createPresenter()
        layerPresenter?.addLayer()
        Mockito.verify(navigator)?.showToast(R.string.layer_too_many_layers, Toast.LENGTH_SHORT)
        Mockito.verifyNoInteractions(
            commandManager,
            layerMenuViewHolder,
            layerAdapter,
            listItemDragHandler
        )
    }

    @Test
    fun testRemoveLayer() {
        val selectedLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        layerModel?.currentLayer = layerModel?.getLayerAt(1)
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createRemoveLayerCommand(1)).thenReturn(command)
        createPresenter()
        layerPresenter?.removeLayer()
        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verifyNoInteractions(
            selectedLayer,
            layerMenuViewHolder,
            layerAdapter,
            listItemDragHandler
        )
    }

    @Test
    fun testRemoveLayerWhenOnlyOneLayer() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        layerPresenter?.removeLayer()
        Mockito.verifyNoInteractions(
            commandManager,
            commandFactory,
            layerMenuViewHolder,
            layerAdapter,
            listItemDragHandler
        )
    }

    @Test
    fun testOnDragLayerAtPosition() {
        val view = Mockito.mock(View::class.java)
        val firstLayer = ZaintLayer(Mockito.mock(Bitmap::class.java))
        val secondLayer = ZaintLayer(Mockito.mock(Bitmap::class.java))
        Assert.assertTrue(firstLayer.isVisible)
        Assert.assertTrue(secondLayer.isVisible)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        createPresenter()
        layerPresenter?.onStartDragging(0, view)
        Mockito.verify(listItemDragHandler)?.beginDrag(0, view)
        Mockito.verifyNoInteractions(
            commandManager,
            commandFactory,
            layerMenuViewHolder,
            layerAdapter
        )
    }

    @Test
    fun testOnDragLayerAtPositionWhenOnlyOneLayer() {
        val view = Mockito.mock(View::class.java)
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        layerPresenter?.onStartDragging(0, view)
        Mockito.verifyNoInteractions(
            listItemDragHandler,
            commandManager,
            commandFactory,
            layerMenuViewHolder,
            layerAdapter
        )
    }

    @Test
    fun testOnDragLayerAtPositionWhenPositionOutOfBounds() {
        val view = Mockito.mock(View::class.java)
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        layerPresenter?.onStartDragging(2, view)
        Mockito.verifyNoInteractions(
            listItemDragHandler,
            commandManager,
            commandFactory,
            layerMenuViewHolder,
            layerAdapter
        )
    }

    @Test
    fun testOnClickLayerAtPositionWhenDeselected() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        layerModel?.currentLayer = layerModel?.getLayerAt(1)
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createSelectLayerCommand(0)).thenReturn(command)
        createPresenter()
        layerPresenter?.setLayerSelected(0)
        Mockito.verify(commandManager)?.addCommand(command)
    }

    @Test
    fun testOnClickLayerAtPositionWhenAlreadySelected() {
        val layer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, layer)
        layerModel?.currentLayer = layer
        createPresenter()
        layerPresenter?.setLayerSelected(0)
        Mockito.verifyNoInteractions(
            commandManager,
            commandFactory,
            layerMenuViewHolder,
            layerAdapter
        )
    }

    @Test
    fun testOnClickLayerAtPositionWhenPositionOutOfBounds() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        layerModel?.currentLayer = firstLayer
        createPresenter()
        layerPresenter?.setLayerSelected(2)
        Mockito.verifyNoInteractions(
            commandManager,
            commandFactory,
            layerMenuViewHolder,
            layerAdapter
        )
    }

    @Test
    fun testSwapItemsVisuallyDoesNotModifyModel() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        createPresenter()
        layerPresenter?.previewMove(0, 1)
        Assert.assertEquals(2, layerModel?.layerCount)
        Assert.assertEquals(0, layerModel?.getLayerIndexOf(firstLayer))
        Assert.assertEquals(1, layerModel?.getLayerIndexOf(secondLayer))
        Mockito.verifyNoInteractions(commandManager, layerMenuViewHolder, firstLayer, secondLayer)
    }

    @Test
    fun testSwapItemsVisually() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        createPresenter()
        layerPresenter?.previewMove(0, 1)
        Assert.assertEquals(2, layerPresenter?.layerCount)
        Assert.assertEquals(firstLayer, layerPresenter?.getLayerItem(1))
        Assert.assertEquals(secondLayer, layerPresenter?.getLayerItem(0))
        Mockito.verifyNoInteractions(commandManager, layerMenuViewHolder, firstLayer, secondLayer)
    }

    @Test
    fun testMergeItems() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createMergeLayersCommand(0, 1)).thenReturn(command)
        createPresenter()
        layerPresenter?.commitMerge(0, 1)
        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verify(navigator)?.showToast(R.string.layer_merged, Toast.LENGTH_SHORT)
        Mockito.verifyNoMoreInteractions(commandManager)
        Mockito.verifyNoInteractions(layerMenuViewHolder, firstLayer, secondLayer, command)
    }

    @Test
    fun testMergeActiveLayers() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        Mockito.`when`(firstLayer.isVisible).thenReturn(true)
        Mockito.`when`(secondLayer.isVisible).thenReturn(true)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createMergeActiveLayersCommand(listOf(0, 1)))
            .thenReturn(command)
        createPresenter()

        layerPresenter?.mergeActiveLayers()

        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verify(commandFactory)?.createMergeActiveLayersCommand(listOf(0, 1))
        Mockito.verifyNoInteractions(layerMenuViewHolder, navigator)
    }

    @Test
    fun testDoesNotMergeWhenOnlyOneLayerIsActive() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        Mockito.`when`(firstLayer.isVisible).thenReturn(true)
        Mockito.`when`(secondLayer.isVisible).thenReturn(false)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        createPresenter()

        layerPresenter?.mergeActiveLayers()

        Mockito.verifyNoInteractions(commandManager, commandFactory, layerMenuViewHolder, navigator)
    }

    @Test
    fun testMergeItemsWhenAtSamePosition() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        createPresenter()
        layerPresenter?.commitMerge(0, 0)
        Mockito.verifyNoInteractions(
            commandManager,
            layerMenuViewHolder,
            firstLayer,
            secondLayer,
            navigator
        )
    }

    @Test
    fun testMergeItemsAfterVisualSwap() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        val thirdLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        layerModel?.addLayerAt(2, thirdLayer)
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createMergeLayersCommand(0, 1)).thenReturn(command)
        createPresenter()
        layerPresenter?.previewMove(0, 1)
        layerPresenter?.commitMerge(0, 0)
        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verify(navigator)?.showToast(R.string.layer_merged, Toast.LENGTH_SHORT)
        Mockito.verifyNoMoreInteractions(commandManager, firstLayer, secondLayer, thirdLayer)
        Mockito.verifyNoInteractions(layerMenuViewHolder, command)
    }

    @Test
    fun testReorderItems() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createReorderLayersCommand(1, 0)).thenReturn(command)
        createPresenter()
        layerPresenter?.commitReorder(1, 0)
        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verifyNoMoreInteractions(commandManager)
        Mockito.verifyNoInteractions(layerMenuViewHolder, firstLayer, secondLayer, command)
    }

    @Test
    fun testReorderItemsAfterVisualSwap() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        val thirdLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        layerModel?.addLayerAt(2, thirdLayer)
        val command = Mockito.mock(Command::class.java)
        Mockito.`when`(commandFactory?.createReorderLayersCommand(2, 0)).thenReturn(command)
        createPresenter()
        layerPresenter?.previewMove(2, 1)
        layerPresenter?.previewMove(1, 0)
        layerPresenter?.commitReorder(2, 0)
        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verifyNoMoreInteractions(commandManager)
        Mockito.verifyNoInteractions(layerMenuViewHolder, firstLayer, secondLayer, command)
    }

    @Test
    fun testReorderItemsWhenAlreadyAtThisPosition() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        createPresenter()
        layerPresenter?.commitReorder(1, 1)
        Mockito.verifyNoInteractions(commandManager, layerMenuViewHolder, firstLayer, secondLayer)
    }

    @Test
    fun testMarkMergeable() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        val layerViewHolder = Mockito.mock(ZaintLayerContracts.LayerViewHolder::class.java)
        Mockito.`when`(layerAdapter?.getViewHolderAt(1)).thenReturn(layerViewHolder)
        createPresenter()
        layerPresenter?.showMergeTarget(0, 1)
        Mockito.verify(layerViewHolder).setMergable()
        Mockito.verifyNoInteractions(commandManager, layerMenuViewHolder, listItemDragHandler)
    }

    @Test
    fun testMarkMergeableWhenPositionInvalidDoesNothing() {
        createPresenter()
        layerPresenter?.showMergeTarget(0, 1)
        Mockito.verifyNoInteractions(commandManager, layerMenuViewHolder, listItemDragHandler)
    }

    @Test
    fun testCommandPostExecuteResetsInternalModel() {
        val firstLayer = Mockito.mock(ZaintLayer::class.java)
        val secondLayer = Mockito.mock(ZaintLayer::class.java)
        layerModel?.addLayerAt(0, firstLayer)
        layerModel?.addLayerAt(1, secondLayer)
        createPresenter()
        layerPresenter?.previewMove(0, 1)
        Assert.assertEquals(secondLayer, layerPresenter?.getLayerItem(0))
        Assert.assertEquals(firstLayer, layerPresenter?.getLayerItem(1))
        layerPresenter?.invalidate()
        Assert.assertEquals(firstLayer, layerPresenter?.getLayerItem(0))
        Assert.assertEquals(secondLayer, layerPresenter?.getLayerItem(1))
    }

    @Test
    fun testCommandPostExecute() {
        layerModel?.addLayerAt(0, Mockito.mock(ZaintLayer::class.java))
        layerModel?.addLayerAt(1, Mockito.mock(ZaintLayer::class.java))
        createPresenter()
        layerPresenter?.invalidate()
        Mockito.verify(layerAdapter)?.notifyDataSetChanged()
        Mockito.verify(layerMenuViewHolder)?.enableAddLayerButton()
        Mockito.verify(layerMenuViewHolder)?.enableRemoveLayerButton()
        Mockito.verify(layerMenuViewHolder)?.disableMergeLayerButton()
    }
}
