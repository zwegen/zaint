package de.zwegen.zpaint.presenter

import android.graphics.PointF
import android.util.Log
import android.view.View
import android.widget.Toast
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.common.MAX_LAYERS
import de.zwegen.zpaint.common.MEGABYTE_IN_BYTE
import de.zwegen.zpaint.common.MINIMUM_HEAP_SPACE_FOR_NEW_LAYER
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.controller.ZaintToolControl
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.implementation.ZaintLineTool
import de.zwegen.zpaint.ui.ZaintDrawingSurface
import de.zwegen.zpaint.ui.dragndrop.LayerDragCoordinator
import de.zwegen.zpaint.ui.dragndrop.LayerDragHandler
import de.zwegen.zpaint.ui.viewholder.BottomNavigationViewHolder
import java.util.Collections.swap

class ZaintLayerPresenter(
    private val model: ZaintLayerContracts.Model,
    private val layerDragHandler: LayerDragHandler,
    private val layerMenuViewHolder: ZaintLayerContracts.LayerMenuViewHolder,
    private val commandManager: ZaintCommandTimeline,
    private val commandFactory: ZaintCommandFactoryApi,
    private val navigator: ZaintLayerContracts.Navigator
) : ZaintLayerContracts.Presenter, LayerDragCoordinator {
    private var adapter: ZaintLayerContracts.Adapter? = null
    private var drawingSurface: ZaintDrawingSurface? = null
    private var toolCoordinator: ZaintToolControl? = null
    private var bottomNavigationViewHolder: BottomNavigationViewHolder? = null
    private val layers: MutableList<ZaintLayerContracts.ZaintLayer>

    override val presenter: ZaintLayerPresenter
        get() = this

    override val layerCount: Int
        get() = layers.size

    companion object {
        private val TAG = ZaintLayerPresenter::class.java.simpleName
    }

    init {
        layers = ArrayList(model.layers)
    }

    override fun getLayerDragHandler(): LayerDragHandler = layerDragHandler

    private fun isPositionValid(position: Int): Boolean = position >= 0 && position < layers.size

    private fun checkIfLineToolInUse() {
        toolCoordinator?.apply {
            if (toolType == ZaintToolKind.LINE) {
                val lineTool = currentTool as ZaintLineTool
                if (!lineTool.lineFinalized && lineTool.startpointSet && !lineTool.endpointSet) {
                    if (commandManager.isUndoAvailable) {
                        commandManager.undoIgnoringColorChanges()
                    }
                    lineTool.startPointToDraw = null
                    lineTool.startpointSet = false
                } else if (!lineTool.lineFinalized && lineTool.startpointSet && lineTool.endpointSet) {
                    lineTool.toolSwitched = true
                    lineTool.onClickOnButton()
                }
            }
        }
    }

    override fun setAdapter(layerAdapter: ZaintLayerContracts.Adapter) {
        this.adapter = layerAdapter
    }

    override fun setDrawingSurface(drawingSurface: ZaintDrawingSurface) {
        this.drawingSurface = drawingSurface
    }

    override fun setToolCoordinator(toolCoordinator: ZaintToolControl) {
        this.toolCoordinator = toolCoordinator
    }

    override fun setBottomNavigationViewHolder(
        bottomNavigationViewHolder: BottomNavigationViewHolder
    ) {
        this.bottomNavigationViewHolder = bottomNavigationViewHolder
    }

    override fun onSelectedLayerInvisible() {
        toolCoordinator?.hideToolOptionsView()
        toolCoordinator?.switchTool(ZaintToolKind.HAND)
        bottomNavigationViewHolder?.showCurrentTool(ZaintToolKind.HAND)
    }

    override fun onSelectedLayerVisible() {
        toolCoordinator?.switchTool(ZaintToolKind.BRUSH)
        bottomNavigationViewHolder?.showCurrentTool(ZaintToolKind.BRUSH)
    }

    override fun refreshLayerMenuViewHolder() {
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / MEGABYTE_IN_BYTE
        val maxHeapSizeInMB = runtime.maxMemory() / MEGABYTE_IN_BYTE
        val availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB

        if (layerCount < MAX_LAYERS && availHeapSizeInMB > MINIMUM_HEAP_SPACE_FOR_NEW_LAYER) {
            layerMenuViewHolder.enableAddLayerButton()
            layerMenuViewHolder.enableDuplicateLayerButton()
        } else {
            layerMenuViewHolder.disableAddLayerButton()
            layerMenuViewHolder.disableDuplicateLayerButton()
        }
        if (layerCount > 1) {
            layerMenuViewHolder.enableRemoveLayerButton()
        } else {
            layerMenuViewHolder.disableRemoveLayerButton()
        }
        if (model.layers.count { it.isVisible } > 1) {
            layerMenuViewHolder.enableMergeLayerButton()
        } else {
            layerMenuViewHolder.disableMergeLayerButton()
        }
    }

    override fun disableVisibilityAndOpacityButtons() {
        layerMenuViewHolder.disableLayerVisibilityButton()
        layerMenuViewHolder.disableLayerOpacityButton()
    }

    override fun isShown(): Boolean = layerMenuViewHolder.isShown()

    override fun getLayerItem(position: Int): ZaintLayerContracts.ZaintLayer? {
        if (isPositionValid(position)) {
            return layers[position]
        } else {
            Log.w("ZaintLayerPresenter.kt", "ZaintLayerPresenter.getLayerItem(position) - tried to access position out of range of the layers array!")
            return null
        }
    }

    override fun getLayerItemId(position: Int): Long = position.toLong()

    override fun addLayer() {
        if (layerCount < MAX_LAYERS) {
            checkIfLineToolInUse()
            commandManager.addCommand(commandFactory.createAddEmptyLayerCommand())
        } else {
            navigator.showToast(R.string.layer_too_many_layers, Toast.LENGTH_SHORT)
        }
    }

    override fun duplicateLayer() {
        if (layerCount < MAX_LAYERS) {
            checkIfLineToolInUse()
            val layerToDuplicate = model.currentLayer ?: return
            val index = model.getLayerIndexOf(layerToDuplicate)
            commandManager.addCommand(commandFactory.createDuplicateLayerCommand(index))
        } else {
            navigator.showToast(R.string.layer_too_many_layers, Toast.LENGTH_SHORT)
        }
    }

    override fun removeLayer() {
        if (layerCount > 1) {
            checkIfLineToolInUse()
            val layerToDelete = model.currentLayer ?: return
            val index = model.getLayerIndexOf(layerToDelete)
            commandManager.addCommand(commandFactory.createRemoveLayerCommand(index))
        }
    }

    override fun mergeActiveLayers() {
        val activeLayerPositions = model.layers.mapIndexedNotNull { index, layer ->
            index.takeIf { layer.isVisible }
        }
        if (activeLayerPositions.size < 2) return

        checkIfLineToolInUse()
        commandManager.addCommand(commandFactory.createMergeActiveLayersCommand(activeLayerPositions))
    }

    private fun getDestinationLayer(
        position: Int,
        isVisible: Boolean
    ): ZaintLayerContracts.ZaintLayer? = model.getLayerAt(position)?.apply {
        this.isVisible = isVisible
    }

    override fun getSelectedLayer(): ZaintLayerContracts.ZaintLayer? = model.currentLayer

    override fun setLayerSelected(position: Int) {
        if (!isPositionValid(position)) {
            Log.e(TAG, "onClickLayerAtPosition at invalid position")
            return
        }
        if (position != model.currentLayer?.let { model.getLayerIndexOf(it) }) {
            checkIfLineToolInUse()
            commandManager.addCommand(commandFactory.createSelectLayerCommand(position))
        }
    }

    override fun selectBottomLayer() {
        val bottomLayer = model.getLayerAt(layerCount - 1) ?: return
        if (model.currentLayer !== bottomLayer) {
            model.currentLayer = bottomLayer
            adapter?.notifyDataSetChanged()
        }
    }

    override fun changeLayerOpacity(position: Int, opacityPercentage: Int) {
        if (!isPositionValid(position)) {
            Log.e(TAG, "invalid layer position to change opacity")
            return
        }

        commandManager.addCommand(commandFactory.createLayerOpacityCommand(position, opacityPercentage))
    }

    override fun setLayerVisibility(position: Int, isVisible: Boolean) {
        refreshDrawingSurface()
        getDestinationLayer(position, isVisible)?.let { layer ->
            layer.isVisible = isVisible
            if (model.currentLayer === layer) {
                if (isVisible) {
                    onSelectedLayerVisible()
                } else {
                    onSelectedLayerInvisible()
                }
            }
        }
        refreshLayerMenuViewHolder()
    }

    override fun refreshDrawingSurface() {
        drawingSurface?.refreshDrawingSurface()
    }

    override fun previewMove(position: Int, swapWith: Int): Int {
        swap(layers, position, swapWith)
        return swapWith
    }

    override fun commitMerge(position: Int, mergeWith: Int) {
        checkIfLineToolInUse()
        layers.getOrNull(mergeWith)?.let { actualLayer ->
            val actualPosition = model.getLayerIndexOf(actualLayer)
            if (position != actualPosition && actualPosition > -1) {
                commandManager.addCommand(
                    commandFactory.createMergeLayersCommand(position, actualPosition)
                )
                navigator.showToast(R.string.layer_merged, Toast.LENGTH_SHORT)
            }
        }
    }

    override fun commitReorder(position: Int, swapWith: Int) {
        if (position != swapWith) {
            checkIfLineToolInUse()
            commandManager.addCommand(commandFactory.createReorderLayersCommand(position, swapWith))
        }
    }

    override fun showMergeTarget(position: Int, mergeWith: Int) {
        if (!isPositionValid(position) || !isPositionValid(mergeWith)) {
            Log.e(TAG, "onLongClickLayerAtPosition at invalid position")
            return
        }
        adapter?.getViewHolderAt(mergeWith)?.setMergable()
    }

    override fun onStopDragging() {
        layerDragHandler.endDrag()
    }

    override fun onStartDragging(position: Int, view: View) {
        if (!isPositionValid(position)) {
            Log.e(TAG, "onLongClickLayerAtPosition at invalid position")
            return
        }
        var isAllowedToLongclick = true
        for (i in layers.indices) {
            if (!layers[i].isVisible) {
                isAllowedToLongclick = false
            }
        }
        if (isAllowedToLongclick) {
            if (layerCount > 1) {
                layerDragHandler.beginDrag(position, view)
            }
        } else {
            navigator.showToast(R.string.no_longclick_on_hidden_layer, Toast.LENGTH_SHORT)
        }
    }

    override fun invalidate() {
        synchronized(model) {
            layers.clear()
            layers.addAll(model.layers)
        }
        refreshLayerMenuViewHolder()
        adapter?.notifyDataSetChanged()
        layerDragHandler.endDrag()
    }

    fun resetMergeColor(layerPosition: Int) {
        adapter?.let { adapter ->
            adapter.getViewHolderAt(layerPosition)?.let { layerViewHolder ->
                val isSelected = layerViewHolder.isSelected()
                adapter.getViewHolderAt(layerPosition)?.setSelected(isSelected)
            }
        }
    }
}
