package de.zwegen.zpaint.model

import android.graphics.Bitmap
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Maximum value used by the layer opacity UI and persistence format. */
const val MAX_LAYER_OPACITY_PERCENTAGE = 100

/** Android's fully opaque alpha channel value. */
const val MAX_LAYER_OPACITY_VALUE = 255

/**
 * Mutable image layer owned by a [ZaintLayerModel].
 *
 * The bitmap remains mutable because drawing tools render into it. Visibility and opacity travel
 * with the bitmap, so compositing has one coherent source of layer state.
 */
open class ZaintLayer(override var bitmap: Bitmap) : ZaintLayerContracts.ZaintLayer {
    override var isVisible: Boolean = true

    override var opacityPercentage: Int = MAX_LAYER_OPACITY_PERCENTAGE
        set(value) {
            field = value.coerceIn(0, MAX_LAYER_OPACITY_PERCENTAGE)
        }

    override fun getValueForOpacityPercentage(): Int =
        (opacityPercentage * MAX_LAYER_OPACITY_VALUE + MAX_LAYER_OPACITY_PERCENTAGE / 2) /
            MAX_LAYER_OPACITY_PERCENTAGE
}
