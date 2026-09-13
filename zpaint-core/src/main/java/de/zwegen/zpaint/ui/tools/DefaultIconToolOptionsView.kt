package de.zwegen.zpaint.ui.tools

import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.options.IconResult
import de.zwegen.zpaint.tools.options.IconToolOptionsView

private const val ICON_GRID_COLUMNS = 4

class DefaultIconToolOptionsView(rootView: ViewGroup) : IconToolOptionsView {
    private var callback: IconToolOptionsView.Callback? = null
    private val searchEditText: EditText
    private val loadMoreButton: Button
    private val statusTextView: AppCompatTextView
    private val resultsGrid: GridLayout
    private val iconToolOptionsView: View

    init {
        val inflater = LayoutInflater.from(rootView.context)
        val iconToolView = inflater.inflate(R.layout.dialog_zpaint_icons, rootView)
        iconToolView.run {
            searchEditText = findViewById(R.id.zpaint_icon_search_edit)
            loadMoreButton = findViewById(R.id.zpaint_icon_load_more_button)
            statusTextView = findViewById(R.id.zpaint_icon_status)
            resultsGrid = findViewById(R.id.zpaint_icon_results_grid)
            iconToolOptionsView = findViewById(R.id.zpaint_layout_icon_tool_options)
        }
        loadMoreButton.visibility = View.GONE
        initializeListeners()
    }

    private fun initializeListeners() {
        loadMoreButton.setOnClickListener { callback?.loadMoreIcons() }
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search()
                true
            } else {
                false
            }
        }
        searchEditText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP &&
                event.x >= searchEditText.width - searchEditText.totalPaddingEnd
            ) {
                search()
                true
            } else {
                false
            }
        }
    }

    private fun search() {
        callback?.searchIcons(searchEditText.text.toString())
    }

    override fun setCallback(callback: IconToolOptionsView.Callback) {
        this.callback = callback
    }

    override fun setShapeSizeText(shapeSize: String) = Unit

    override fun toggleShapeSizeVisibility(isVisible: Boolean) = Unit

    override fun getIconToolOptionsLayout(): View = iconToolOptionsView

    override fun setLoading(isLoading: Boolean) {
        searchEditText.isEnabled = !isLoading
        loadMoreButton.isEnabled = !isLoading
        if (isLoading) {
            statusTextView.text = searchEditText.context.getString(R.string.icon_tool_loading)
            statusTextView.visibility = View.VISIBLE
        }
    }

    override fun showMessage(message: String) {
        statusTextView.text = message
        statusTextView.visibility = View.VISIBLE
    }

    override fun showMessage(@StringRes message: Int) {
        showMessage(statusTextView.context.getString(message))
    }

    override fun showResults(results: List<IconResult>, canLoadMore: Boolean) {
        resultsGrid.removeAllViews()
        statusTextView.visibility = View.GONE
        results.forEach { result ->
            resultsGrid.addView(createIconButton(result))
        }
        loadMoreButton.visibility = if (canLoadMore) View.VISIBLE else View.GONE
    }

    private fun createIconButton(result: IconResult): ImageButton {
        val context = resultsGrid.context
        val buttonSize = context.resources.getDimensionPixelSize(R.dimen.zpaint_icon_result_size)
        return ImageButton(context).apply {
            setImageBitmap(result.preview)
            contentDescription = result.id
            background = ContextCompat.getDrawable(context, R.drawable.zpaint_selectable_button_bg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(8, 8, 8, 8)
            setOnClickListener { callback?.selectIcon(result) }
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = buttonSize
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setGravity(Gravity.FILL)
                setMargins(4, 4, 4, 4)
            }
        }
    }
}
