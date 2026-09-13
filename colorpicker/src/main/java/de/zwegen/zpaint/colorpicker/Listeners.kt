package de.zwegen.zpaint.colorpicker

fun interface OnColorChangedListener {
    fun colorChanged(color: Int)
}

fun interface OnColorPickedListener {
    fun colorChanged(color: Int)
}

fun interface OnImageViewPointClickedListener {
    fun colorChanged(color: Int)
}

fun interface OnColorInHistoryChangedListener {
    fun colorInHistoryChanged(color: Int)
}
