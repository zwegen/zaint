package de.zwegen.zpaint.command.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Inclusive document coordinates selected for a crop operation. */
data class CropRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun targetSize(
        documentWidth: Int,
        documentHeight: Int,
        maximumBitmapResolution: Int
    ): CropSize? {
        if (right < left || bottom < top) return null
        if (left >= documentWidth || right < 0 || top >= documentHeight || bottom < 0) return null
        if (left == 0 && top == 0 && right == documentWidth - 1 && bottom == documentHeight - 1) {
            return null
        }

        val width = right + 1 - left
        val height = bottom + 1 - top
        if (width.toLong() * height.toLong() > maximumBitmapResolution) return null
        return CropSize(width, height)
    }
}

data class CropSize(val width: Int, val height: Int)

interface LayerCropper {
    fun crop(layer: ZaintLayerContracts.ZaintLayer, region: CropRegion, targetSize: CropSize)
}

/** Android bitmap adapter for the crop operation. The command itself only owns document rules. */
class AndroidLayerCropper : LayerCropper {
    override fun crop(layer: ZaintLayerContracts.ZaintLayer, region: CropRegion, targetSize: CropSize) {
        val source = layer.bitmap
        val cropped = Bitmap.createBitmap(targetSize.width, targetSize.height, source.config)
        Canvas(cropped).drawBitmap(source, -region.left.toFloat(), -region.top.toFloat(), null)
        layer.bitmap = cropped
    }
}
