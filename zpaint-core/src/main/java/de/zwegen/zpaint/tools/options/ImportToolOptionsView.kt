package de.zwegen.zpaint.tools.options

/** Presents the optional size control while Zaint imports a drawable shape. */
interface ImportToolOptionsView {
    fun setShapeSizeText(shapeSize: String)

    fun setShapeSizeInvisble()

    fun toggleShapeSizeVisibility(isVisible: Boolean)
}
