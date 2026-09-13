package de.zwegen.zpaint.iotasks

import android.graphics.Bitmap
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.model.CommandManagerModel

/** Bundles the independent result variants produced by Zaint's image-loading tasks. */
data class BitmapReturnValue(
    @JvmField
    var model: CommandManagerModel?,
    @JvmField
    var layerList: List<ZaintLayerContracts.ZaintLayer>?,
    @JvmField
    var bitmap: Bitmap?,
    @JvmField
    var toBeScaled: Boolean,
    @JvmField
    var colorHistory: ColorHistory?
) {
    constructor(bitmapList: List<ZaintLayerContracts.ZaintLayer>?, bitmap: Bitmap?, toBeScaled: Boolean) : this(
        null,
        bitmapList,
        bitmap,
        toBeScaled,
        null
    )

    constructor(model: CommandManagerModel?, colorHistory: ColorHistory?) : this(model, null, null, false, colorHistory)
}
