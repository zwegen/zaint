package de.zwegen.zpaint.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.options.ImportToolOptionsView

class DefaultImportToolOptionsView(root: ViewGroup) : ImportToolOptionsView {
    init {
        LayoutInflater.from(root.context).inflate(
            R.layout.dialog_zpaint_import_tool,
            root
        )
    }

    override fun setShapeSizeText(shapeSize: String) = Unit

    override fun setShapeSizeInvisble() = Unit

    override fun toggleShapeSizeVisibility(isVisible: Boolean) = Unit
}
