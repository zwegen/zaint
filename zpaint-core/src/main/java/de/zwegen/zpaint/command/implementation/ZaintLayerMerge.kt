package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import de.zwegen.zpaint.command.LayerModelCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Flattens a source layer into a destination layer without changing the source document on failure. */
class ZaintLayerMerge(sourceIndex: Int, destinationIndex: Int) : LayerModelCommand() {
    private val merge = LayerMerge(sourceIndex, destinationIndex)

    val sourceIndex: Int
        get() = merge.sourceIndex

    val destinationIndex: Int
        get() = merge.destinationIndex

    override fun applyTo(layerModel: ZaintLayerContracts.Model) {
        if (merge.sourceIndex == merge.destinationIndex) return

        val source = layerModel.getLayerAt(merge.sourceIndex) ?: return
        val destination = layerModel.getLayerAt(merge.destinationIndex) ?: return
        val sourceBitmap = source.bitmap ?: return
        val destinationBitmap = destination.bitmap ?: return
        val mergedBitmap = Bitmap.createBitmap(
            destinationBitmap.width,
            destinationBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        Canvas(mergedBitmap).apply {
            drawBitmap(destinationBitmap, 0f, 0f, Paint().apply {
                alpha = destination.getValueForOpacityPercentage()
            })
            drawBitmap(sourceBitmap, 0f, 0f, Paint().apply {
                alpha = source.getValueForOpacityPercentage()
            })
        }
        if (!layerModel.removeLayerAt(merge.sourceIndex)) {
            mergedBitmap.recycle()
            return
        }

        destination.bitmap = mergedBitmap
        destination.opacityPercentage = 100
        if (layerModel.currentLayer === source) {
            layerModel.currentLayer = destination
        }
    }

    private data class LayerMerge(val sourceIndex: Int, val destinationIndex: Int)
}
