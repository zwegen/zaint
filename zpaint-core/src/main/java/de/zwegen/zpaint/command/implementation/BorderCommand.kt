/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts

class BorderCommand(
    borderAmount: Int,
    borderColor: Int,
    maximumBitmapResolution: Int
) : Command {

    var borderAmount = borderAmount; private set
    var borderColor = borderColor; private set
    var maximumBitmapResolution = maximumBitmapResolution; private set

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        applyBorder(layerModel, borderAmount, borderColor, maximumBitmapResolution)
    }

    override fun freeResources() {
        // No resources to free
    }

    companion object {
        fun applyBorder(
            layerModel: ZaintLayerContracts.Model,
            amount: Int,
            color: Int,
            maximumBitmapResolution: Int
        ): Boolean {
            if (amount == 0 || layerModel.layerCount == 0) {
                return false
            }

            val sourceWidth = layerModel.width
            val sourceHeight = layerModel.height
            val effectiveAmount = effectiveAmount(amount, sourceWidth, sourceHeight)
            if (effectiveAmount == 0) {
                return false
            }

            val targetWidth = sourceWidth + effectiveAmount * 2
            val targetHeight = sourceHeight + effectiveAmount * 2
            if (targetWidth < 1 || targetHeight < 1 ||
                targetWidth.toLong() * targetHeight.toLong() > maximumBitmapResolution
            ) {
                return false
            }

            val bottomLayerIndex = layerModel.layerCount - 1
            layerModel.layers.forEachIndexed { index, layer ->
                layer.bitmap = createBorderBitmap(
                    sourceBitmap = layer.bitmap,
                    amount = effectiveAmount,
                    color = color,
                    fillBorder = index == bottomLayerIndex
                )
            }
            layerModel.width = targetWidth
            layerModel.height = targetHeight
            return true
        }

        fun createBorderBitmap(
            sourceBitmap: Bitmap,
            amount: Int,
            color: Int,
            fillBorder: Boolean
        ): Bitmap {
            val sourceWidth = sourceBitmap.width
            val sourceHeight = sourceBitmap.height
            val effectiveAmount = effectiveAmount(amount, sourceWidth, sourceHeight)
            val targetWidth = sourceWidth + effectiveAmount * 2
            val targetHeight = sourceHeight + effectiveAmount * 2
            val targetBitmap = Bitmap.createBitmap(
                targetWidth.coerceAtLeast(1),
                targetHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val targetCanvas = Canvas(targetBitmap)
            if (effectiveAmount > 0) {
                if (fillBorder) {
                    targetCanvas.drawColor(color)
                }
                targetCanvas.drawBitmap(sourceBitmap, effectiveAmount.toFloat(), effectiveAmount.toFloat(), null)
            } else {
                val crop = -effectiveAmount
                targetCanvas.drawBitmap(sourceBitmap, -crop.toFloat(), -crop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return targetBitmap
        }

        private fun effectiveAmount(amount: Int, width: Int, height: Int): Int {
            if (amount >= 0) {
                return amount
            }
            val maximumCrop = ((minOf(width, height) - 1) / 2).coerceAtLeast(0)
            return -((-amount).coerceAtMost(maximumCrop))
        }
    }
}
