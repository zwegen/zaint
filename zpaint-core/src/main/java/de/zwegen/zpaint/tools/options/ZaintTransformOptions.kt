package de.zwegen.zpaint.tools.options

import de.zwegen.zpaint.ui.tools.NumberRangeFilter

/**
 * Contract between Zaint's adjust tool and its size/aspect-ratio controls.
 */
interface ZaintTransformOptions {
    enum class AspectRatio(val width: Int, val height: Int) {
        FREE(0, 0),
        SQUARE(1, 1),
        FOUR_FIVE(4, 5),
        FIVE_FOUR(5, 4),
        SIXTEEN_NINE(16, 9),
        NINE_SIXTEEN(9, 16)
    }

    fun setWidthRange(filter: NumberRangeFilter)

    fun setHeightRange(filter: NumberRangeFilter)

    fun setListener(listener: Listener)

    fun showWidth(width: Int)

    fun showHeight(height: Int)

    fun showResizePercent(percent: Int)

    interface Listener {
        fun onRotateLeft()

        fun onRotateRight()

        fun onFlipHorizontal()

        fun onFlipVertical()

        fun onApplyResize(percent: Int)

        fun onResizePercentChanged(percent: Int)

        fun onWidthChanged(width: Float)

        fun onHeightChanged(height: Float)

        fun onCurrentLayerOnlyChanged(currentLayerOnly: Boolean)

        fun onAspectRatioChanged(aspectRatio: AspectRatio)

        fun onHideRequested()
    }
}
