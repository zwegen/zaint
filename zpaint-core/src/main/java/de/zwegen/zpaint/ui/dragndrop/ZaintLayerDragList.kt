package de.zwegen.zpaint.ui.dragndrop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.zwegen.zpaint.presenter.ZaintLayerPresenter
import de.zwegen.zpaint.ui.ZaintLayerListAdapter
import kotlin.math.max
import kotlin.math.min

private const val SCROLL_UP = -1
private const val SCROLL_DOWN = 1
private const val THREEHUNDRED = 300
private const val FIVE = 5

class ZaintLayerDragList : RecyclerView, LayerDragHandler {
    private var view: View? = null
    private var hoveringListItem: BitmapDrawable? = null
    private var viewBounds: Rect? = null
    private var coordinator: LayerDragCoordinator? = null
    private var dragSession: LayerDragSession? = null
    private var downY = 0f
    private var offsetToCenter = 0
    private var scrollAmount = 0
    private lateinit var layerAdapter: ZaintLayerListAdapter
    internal lateinit var manager: LinearLayoutManager

    constructor(context: Context) : super(context)

    constructor(context: Context, attributes: AttributeSet?) : super(context, attributes)

    constructor(context: Context, attributes: AttributeSet?, defStyle: Int) : super(
        context,
        attributes,
        defStyle
    )

    init {
        setHasFixedSize(true)
        adapter?.setHasStableIds(true)
    }

    fun setCoordinator(coordinator: LayerDragCoordinator) {
        this.coordinator = coordinator
    }

    fun setLayerAdapter(adapter: ZaintLayerListAdapter) {
        this.layerAdapter = adapter
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (hoveringListItem == null || dragSession == null) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                handleTouchUp()
                downY = event.y
                notifyDataSetChanged()
                parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> endDrag()
            MotionEvent.ACTION_DOWN -> downY = event.y
            MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val dY = event.y - downY
                downY += dY
                downY -= offsetToCenter
                viewBounds?.let {
                    it.offsetTo(it.left, downY.toInt())
                    val top = min(height - it.height(), max(0, it.top))
                    val bottom = top + it.height()
                    hoveringListItem?.setBounds(it.left, top, it.right, bottom)
                }
                val didScroll = handleScroll(viewBounds)
                invalidate()
                if (!didScroll) swapListItems()
            }
        }
        return true
    }

    private fun handleScroll(bounds: Rect?): Boolean {
        val hoverViewTop = bounds?.top
        val hoverHeight = bounds?.height()
        if (scrollAmount == 0) {
            scrollAmount = height / FIVE
        }
        if (hoverViewTop != null && canScrollVertically(SCROLL_UP) && hoverViewTop <= 0) {
            val firstVisible = manager.findFirstVisibleItemPosition()
            smoothScrollBy(0, -scrollAmount)
            swapItems(firstVisible, true)
            return true
        }

        if (hoverViewTop != null && hoverHeight != null && canScrollVertically(SCROLL_DOWN) &&
            hoverViewTop > height - THREEHUNDRED
        ) {
            val lastVisible = manager.findLastVisibleItemPosition()
            smoothScrollBy(0, scrollAmount)
            swapItems(lastVisible, true)
            return true
        }
        return false
    }

    override fun beginDrag(position: Int, view: View) {
        this.view?.visibility = VISIBLE
        this.view = view
        dragSession = LayerDragSession(position)
        view.visibility = INVISIBLE
        hoveringListItem = getHoveringListItem(view)
        setOffsetToCenter(viewBounds)
        invalidate()
    }

    override fun endDrag() {
        hoveringListItem ?: return
        dragSession?.mergeTarget?.let { mergeTarget ->
            (coordinator as? ZaintLayerPresenter)?.resetMergeColor(mergeTarget)
        }
        dragSession = null
        view?.visibility = VISIBLE
        view = null
        hoveringListItem = null
        invalidate()
        notifyDataSetChanged()
    }

    public override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        hoveringListItem?.draw(canvas)
    }

    private fun getHoveringListItem(view: View): BitmapDrawable {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        val colorFilter = LightingColorFilter(BRIGHTNESS_MUL_VALUE, BRIGHTNESS_ADD_VALUE)
        val drawable = BitmapDrawable(resources, bitmap)
        drawable.colorFilter = colorFilter
        viewBounds = Rect(view.left, view.top, view.right, view.bottom)
        viewBounds?.let {
            drawable.bounds = it
        }
        drawable.alpha = ALPHA_VALUE
        return drawable
    }

    private fun setOffsetToCenter(viewBounds: Rect?) {
        viewBounds ?: return
        offsetToCenter = viewBounds.height() / 2
    }

    private fun getItemAbove(itemPositionAbove: Int): View? =
        if (isPositionValid(itemPositionAbove)) layerAdapter.getViewHolderAt(itemPositionAbove)?.view else null

    private fun getItemBelow(itemPositionBelow: Int): View? =
        if (isPositionValid(itemPositionBelow)) layerAdapter.getViewHolderAt(itemPositionBelow)?.view else null

    private fun swapItems(swapWith: Int, isScrolling: Boolean) {
        val session = dragSession ?: return
        val previousPosition = session.currentPosition
        coordinator?.let { session.movePreviewTo(it.previewMove(previousPosition, swapWith)) }
        view?.visibility = VISIBLE
        if (isScrolling) {
            adapter?.notifyItemMoved(previousPosition, swapWith)
        } else {
            notifyDataSetChanged()
        }
    }

    private fun previewMergeAt(mergeWith: Int) {
        val session = dragSession ?: return
        coordinator?.showMergeTarget(session.currentPosition, mergeWith)
        session.selectMergeTarget(mergeWith)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDataSetChanged() {
        this.adapter?.notifyDataSetChanged()
        this.layerAdapter.clearViewHolders()
    }

    private fun swapListItems() {
        val session = dragSession ?: return
        val itemPositionAbove = session.currentPosition - 1
        val itemPositionBelow = session.currentPosition + 1
        val itemBelow = getItemBelow(itemPositionBelow)
        val itemAbove = getItemAbove(itemPositionAbove)
        var canMergeUpwards = false
        var canMergeDownwards = false
        var isAbove = false
        var isBelow = false
        if (itemAbove != null) {
            isBelow = downY < itemAbove.y
            view?.let {
                canMergeUpwards = downY < itemAbove.y + it.height / 2f
            }
        }
        if (itemBelow != null) {
            isAbove = downY > itemBelow.y
            view?.let {
                canMergeDownwards = downY > itemBelow.y - it.height / 2f
            }
        }
        if (isAbove || isBelow) {
            val swapWith = if (isAbove) itemPositionBelow else itemPositionAbove
            swapItems(swapWith, false)
        } else if (canMergeUpwards || canMergeDownwards) {
            val mergeWith = if (canMergeUpwards) itemPositionAbove else itemPositionBelow
            previewMergeAt(mergeWith)
        } else if (session.clearMergeTarget()) {
            notifyDataSetChanged()
        }
    }

    private fun isPositionValid(position: Int): Boolean = position in 0 until layerAdapter.itemCount

    private fun handleTouchUp() {
        when (val outcome = dragSession?.finish() ?: return) {
            is LayerDragSession.Outcome.Merge -> {
                coordinator?.commitMerge(outcome.sourcePosition, outcome.targetPosition)
            }
            is LayerDragSession.Outcome.Reorder -> {
                coordinator?.commitReorder(outcome.sourcePosition, outcome.targetPosition)
            }
        }
        endDrag()
    }

    companion object {
        private const val ALPHA_VALUE = 192
        private const val BRIGHTNESS_MUL_VALUE = 0xffffff
        private const val BRIGHTNESS_ADD_VALUE = 0x222222
    }
}
