package de.zwegen.zpaint.tools.options

import android.graphics.Bitmap
import android.view.View
import androidx.annotation.StringRes

interface IconToolOptionsView {
    fun setCallback(callback: Callback)

    fun setShapeSizeText(shapeSize: String)

    fun toggleShapeSizeVisibility(isVisible: Boolean)

    fun getIconToolOptionsLayout(): View

    fun setLoading(isLoading: Boolean)

    fun showMessage(message: String)

    fun showMessage(@StringRes message: Int)

    fun showResults(results: List<IconResult>, canLoadMore: Boolean)

    interface Callback {
        fun searchIcons(query: String)

        fun loadMoreIcons()

        fun selectIcon(icon: IconResult)
    }
}

data class IconResult(
    val id: String,
    val preview: Bitmap
)
