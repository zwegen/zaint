package de.zwegen.zpaint.ui.viewholder

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.zwegen.zpaint.R
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.ui.BottomNavigationPortrait

/**
 * Zaint's adapter for the fixed navigation below the canvas. It keeps the
 * current-tool tile and color preview in sync without making tool decisions.
 */
class BottomNavigationViewHolder(
    private val layout: View
) : ZaintEditorContracts.BottomNavigationViewHolder {
    val bottomNavigationView: BottomNavigationView =
        layout.findViewById(R.id.zpaint_bottom_navigation)
    private val appearance: ZaintEditorContracts.BottomNavigationAppearance =
        BottomNavigationPortrait(bottomNavigationView)
    private val colorItem: View = itemFor(R.id.action_color_picker)
    private val colorPreview: ImageView =
        colorItem.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view)

    init {
        prepareColorPreview()
    }

    override fun show() = setVisible(true)

    override fun hide() = setVisible(false)

    override fun showCurrentTool(toolType: ZaintToolKind?) {
        if (toolType != null) {
            appearance.showCurrentTool(toolType)
        }
    }

    override fun enableColorItemView(show: Boolean) {
        colorItem.isClickable = show
    }

    override fun setColorButtonColor(color: Int) {
        if (Color.alpha(color) == 0) {
            colorPreview.clearColorFilter()
        } else {
            colorPreview.setColorFilter(color)
        }
    }

    private fun itemFor(itemId: Int): View {
        val menuView = bottomNavigationView.getChildAt(0) as ViewGroup
        val index = (0 until bottomNavigationView.menu.size()).firstOrNull {
            bottomNavigationView.menu.getItem(it).itemId == itemId
        } ?: error("Missing bottom navigation item: $itemId")
        return menuView.getChildAt(index)
    }

    private fun prepareColorPreview() {
        val previewPadding = (3 * layout.resources.displayMetrics.density).toInt()
        colorPreview.imageTintList = null
        colorPreview.scaleType = ImageView.ScaleType.FIT_XY
        colorPreview.setBackgroundResource(R.drawable.zpaint_bottom_navigation_color_preview_border)
        colorPreview.setPadding(previewPadding, previewPadding, previewPadding, previewPadding)
    }

    private fun setVisible(visible: Boolean) {
        layout.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
