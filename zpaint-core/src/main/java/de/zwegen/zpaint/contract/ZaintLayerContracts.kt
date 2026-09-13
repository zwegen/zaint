package de.zwegen.zpaint.contract

import android.graphics.Bitmap
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import de.zwegen.zpaint.controller.ZaintToolControl
import de.zwegen.zpaint.ui.ZaintDrawingSurface
import de.zwegen.zpaint.ui.dragndrop.LayerDragHandler
import de.zwegen.zpaint.ui.viewholder.BottomNavigationViewHolder

interface ZaintLayerContracts {
    interface Adapter {
        fun notifyDataSetChanged()

        fun getViewHolderAt(position: Int): LayerViewHolder?
    }

    interface Presenter {
        val layerCount: Int
        val presenter: Presenter

        fun onSelectedLayerInvisible()

        fun onSelectedLayerVisible()

        fun getLayerDragHandler(): LayerDragHandler

        fun refreshLayerMenuViewHolder()

        fun disableVisibilityAndOpacityButtons()

        fun getLayerItem(position: Int): ZaintLayer?

        fun getLayerItemId(position: Int): Long

        fun addLayer()

        fun duplicateLayer()

        fun removeLayer()

        fun mergeActiveLayers()

        fun changeLayerOpacity(position: Int, opacityPercentage: Int)

        fun setLayerVisibility(position: Int, isVisible: Boolean)

        fun refreshDrawingSurface()

        fun setAdapter(layerAdapter: Adapter)

        fun setDrawingSurface(drawingSurface: ZaintDrawingSurface)

        fun invalidate()

        fun setToolCoordinator(toolCoordinator: ZaintToolControl)

        fun setBottomNavigationViewHolder(bottomNavigationViewHolder: BottomNavigationViewHolder)

        fun isShown(): Boolean

        fun onStartDragging(position: Int, view: View)

        fun onStopDragging()

        fun setLayerSelected(position: Int)

        fun selectBottomLayer()

        fun getSelectedLayer(): ZaintLayer?
    }

    interface LayerViewHolder {
        val bitmap: Bitmap?
        val view: View

        fun setSelected(isSelected: Boolean)

        fun updateImageView(layer: ZaintLayer)

        fun setMergable()

        fun isSelected(): Boolean

        fun getViewLayout(): LinearLayout

        fun bindView()

        fun setLayerVisibilityCheckbox(setTo: Boolean)
    }

    interface LayerMenuViewHolder {
        fun disableAddLayerButton()

        fun enableAddLayerButton()

        fun disableRemoveLayerButton()

        fun enableRemoveLayerButton()

        fun disableDuplicateLayerButton()

        fun enableDuplicateLayerButton()

        fun disableMergeLayerButton()

        fun enableMergeLayerButton()

        fun disableLayerOpacityButton()

        fun disableLayerVisibilityButton()

        fun isShown(): Boolean
    }

    interface ZaintLayer {
        var bitmap: Bitmap
        var isVisible: Boolean
        var opacityPercentage: Int

        fun getValueForOpacityPercentage(): Int
    }

    interface Model {
        val layers: List<ZaintLayer>
        var currentLayer: ZaintLayer?
        var width: Int
        var height: Int
        val layerCount: Int

        fun reset()

        fun getLayerAt(index: Int): ZaintLayer?

        fun getLayerIndexOf(layer: ZaintLayer): Int

        fun addLayerAt(index: Int, layer: ZaintLayer): Boolean

        fun listIterator(index: Int): ListIterator<ZaintLayer>

        fun setLayerAt(position: Int, layer: ZaintLayer)

        fun removeLayerAt(position: Int): Boolean

        fun getBitmapOfAllLayers(): Bitmap?

        fun getBitmapListOfAllLayers(): List<Bitmap?>
    }

    interface Navigator {
        fun showToast(@StringRes id: Int, length: Int)
    }
}
