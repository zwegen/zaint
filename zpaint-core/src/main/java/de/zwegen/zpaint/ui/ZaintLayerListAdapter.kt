package de.zwegen.zpaint.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.ZaintLayoutDirection
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.MAX_LAYER_OPACITY_PERCENTAGE
private const val CORNER_RADIUS = 20f
private const val RESIZE_LENGTH = 400f

class ZaintLayerListAdapter(
    val presenter: ZaintLayerContracts.Presenter,
    val mainActivity: ZaintEditorActivity,
) : RecyclerView.Adapter<ZaintLayerListAdapter.LayerViewHolder>(), ZaintLayerContracts.Adapter {
    private val viewHolders: SparseArray<ZaintLayerContracts.LayerViewHolder> = SparseArray()

    init {
        backgroundContext = mainActivity.applicationContext
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayerViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.zpaint_item_layer, parent, false)
        return LayerViewHolder(itemView, presenter)
    }

    override fun onBindViewHolder(holder: LayerViewHolder, position: Int) {
        viewHolders.put(position, holder)
        holder.bindView()
    }

    override fun getItemCount(): Int = presenter.layerCount

    fun clearViewHolders() {
        viewHolders.clear()
    }

    override fun getViewHolderAt(position: Int): ZaintLayerContracts.LayerViewHolder? = viewHolders[position]

    inner class LayerViewHolder(
        itemView: View,
        private val layerPresenter: ZaintLayerContracts.Presenter
    ) : ZaintLayerContracts.LayerViewHolder, RecyclerView.ViewHolder(itemView) {
        private val layerBackground: LinearLayout = itemView.findViewById(R.id.zpaint_item_layer)
        private val imageView: ImageView = itemView.findViewById(R.id.zpaint_item_layer_image)
        private val dragHandle: ImageView = itemView.findViewById(R.id.zpaint_layer_drag_handle)
        private val opacitySeekBar: SeekBar = itemView.findViewById(R.id.zpaint_layer_opacity_seekbar)
        private var currentBitmap: Bitmap? = null
        private val layerVisibilityCheckbox: CheckBox = itemView.findViewById(R.id.zpaint_checkbox_layer)
        private var isSelected = false

        override val bitmap: Bitmap?
            get() = currentBitmap

        override val view: View
            get() = itemView

        override fun bindView() {
            val layer = layerPresenter.getLayerItem(position) ?: return
            val isSelected = layer === layerPresenter.getSelectedLayer()
            setSelected(isSelected)
            setLayerVisibilityCheckbox(layer.isVisible)
            updateImageView(layer)

            layerVisibilityCheckbox.setOnClickListener {
                val isVisible = layerVisibilityCheckbox.isChecked
                layerPresenter.setLayerVisibility(position, isVisible)
            }

            layerBackground.setOnClickListener {
                layerPresenter.setLayerSelected(position)
            }

            dragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> layerPresenter.onStartDragging(position, itemView)
                    MotionEvent.ACTION_UP -> layerPresenter.onStopDragging()
                }

                true
            }

            opacitySeekBar.progress = layer.opacityPercentage

            opacitySeekBar.setOnTouchListener { view, _ ->
                view.parent.requestDisallowInterceptTouchEvent(true)
                false
            }

            opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    imageView.alpha = progress.toFloat() / MAX_LAYER_OPACITY_PERCENTAGE
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    layerPresenter.changeLayerOpacity(position, seekBar.progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            })
        }

        override fun setSelected(isSelected: Boolean) {
            val background = when (getBackgroundType()) {
                BackgroundType.SINGLE -> getSingleBackground()
                BackgroundType.TOP -> getTopBackground(isSelected)
                BackgroundType.BOTTOM -> getBottomBackground(isSelected)
                BackgroundType.CENTER -> getCenterBackground(isSelected)
            }
            layerBackground.background = background
            this.isSelected = isSelected
        }

        override fun isSelected(): Boolean = isSelected

        override fun getViewLayout(): LinearLayout = layerBackground

        override fun updateImageView(layer: ZaintLayerContracts.ZaintLayer) {
            runBlocking {
                launch {
                    imageView.setImageBitmap(resizeBitmap(layer.bitmap))
                    imageView.alpha = layer.opacityPercentage.toFloat() / MAX_LAYER_OPACITY_PERCENTAGE
                }
            }
            currentBitmap = bitmap
        }

        private fun resizeBitmap(bitmap: Bitmap): Bitmap {
            val newWidth: Float
            val newHeight: Float
            if (bitmap.width > bitmap.height) {
                newWidth = RESIZE_LENGTH
                newHeight = RESIZE_LENGTH * (bitmap.height.toFloat() / bitmap.width.toFloat()) + 1
            } else {
                newWidth = RESIZE_LENGTH * (bitmap.width.toFloat() / bitmap.height.toFloat()) + 1
                newHeight = RESIZE_LENGTH
            }
            return Bitmap.createScaledBitmap(bitmap, newWidth.toInt(), newHeight.toInt(), false)
        }

        override fun setLayerVisibilityCheckbox(setTo: Boolean) {
            layerVisibilityCheckbox.isChecked = setTo
        }

        override fun setMergable() = layerBackground.setBackgroundResource(R.color.zpaint_color_merge_layer)

        private fun getBackgroundType(): BackgroundType {
            if (presenter.layerCount > 2 && this.adapterPosition > 0 && this.adapterPosition < presenter.layerCount - 1) {
                return BackgroundType.CENTER
            }
            if (presenter.layerCount == 1) {
                return BackgroundType.SINGLE
            }
            if (this.adapterPosition == presenter.layerCount - 1) {
                return BackgroundType.BOTTOM
            }
            return BackgroundType.TOP
        }
    }

    private fun getSingleBackground(): Drawable = drawable(R.drawable.layer_item_selected).apply {
        cornerRadii = cornerRadii(mainActivity, BackgroundType.SINGLE)
    }

    private fun getTopBackground(isSelected: Boolean): Drawable = drawable(
        if (isSelected) R.drawable.layer_item_selected else R.drawable.layer_item_unselected
    ).apply { cornerRadii = cornerRadii(mainActivity, BackgroundType.TOP) }

    private fun getBottomBackground(isSelected: Boolean): Drawable = drawable(
        if (isSelected) R.drawable.layer_item_selected else R.drawable.layer_item_unselected
    ).apply { cornerRadii = cornerRadii(mainActivity, BackgroundType.BOTTOM) }

    private fun getCenterBackground(isSelected: Boolean): Drawable = drawable(
        if (isSelected) R.drawable.layer_item_selected else R.drawable.layer_item_unselected
    )

    private fun drawable(resourceId: Int): GradientDrawable = requireNotNull(
        ContextCompat.getDrawable(mainActivity, resourceId) as? GradientDrawable
    ) { "ZaintLayer background is missing" }

    companion object {
        private var backgroundContext: Context? = null

        fun getSingleBackground(): Drawable? = previewDrawable(
            R.drawable.layer_item_selected,
            BackgroundType.SINGLE
        )

        fun getTopBackground(isSelected: Boolean): Drawable? = previewDrawable(
            if (isSelected) R.drawable.layer_item_selected else R.drawable.layer_item_unselected,
            BackgroundType.TOP
        )

        fun getBottomBackground(isSelected: Boolean): Drawable? = previewDrawable(
            if (isSelected) R.drawable.layer_item_selected else R.drawable.layer_item_unselected,
            BackgroundType.BOTTOM
        )

        fun getCenterBackground(isSelected: Boolean): Drawable? = previewDrawable(
            if (isSelected) R.drawable.layer_item_selected else R.drawable.layer_item_unselected,
            BackgroundType.CENTER
        )

        private fun previewDrawable(resourceId: Int, type: BackgroundType): Drawable? {
            val context = backgroundContext ?: return null
            return (ContextCompat.getDrawable(context, resourceId) as? GradientDrawable)?.apply {
                cornerRadii = cornerRadii(context, type)
            }
        }

        private fun cornerRadii(context: Context, backgroundType: BackgroundType): FloatArray {
            val cornerRadius = CORNER_RADIUS * context.resources.displayMetrics.density
            return when (backgroundType) {
                BackgroundType.TOP -> if (ZaintLayoutDirection.usesRightToLeftLayout()) {
                    floatArrayOf(0f, 0f, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f)
                } else {
                    floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, 0f, 0f)
                }
                BackgroundType.BOTTOM -> if (ZaintLayoutDirection.usesRightToLeftLayout()) {
                    floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, 0f, 0f)
                } else {
                    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius)
                }
                BackgroundType.SINGLE -> if (ZaintLayoutDirection.usesRightToLeftLayout()) {
                    floatArrayOf(0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f)
                } else {
                    floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius)
                }
                else -> FloatArray(8)
            }
        }
    }

    enum class BackgroundType {
        TOP,
        BOTTOM,
        CENTER,
        SINGLE
    }
}
