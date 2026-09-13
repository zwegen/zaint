package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor

/** Detects whether a proposed drawing would cover any non-transparent current-layer pixel. */
object VisiblePixelOverlap {
    fun transformedBounds(
        center: PointF,
        width: Float,
        height: Float,
        rotation: Float,
        padding: Float = 0f
    ): RectF = RectF(
        center.x - width / 2f,
        center.y - height / 2f,
        center.x + width / 2f,
        center.y + height / 2f
    ).also { bounds ->
        Matrix().apply {
            setRotate(rotation, center.x, center.y)
            mapRect(bounds)
        }
        bounds.inset(-padding, -padding)
    }

    fun overlaps(
        currentLayer: Bitmap?,
        proposedBounds: RectF,
        drawProposal: (Canvas) -> Unit
    ): Boolean {
        currentLayer ?: return false
        val left = floor(proposedBounds.left).toInt().coerceIn(0, currentLayer.width)
        val top = floor(proposedBounds.top).toInt().coerceIn(0, currentLayer.height)
        val right = ceil(proposedBounds.right).toInt().coerceIn(0, currentLayer.width)
        val bottom = ceil(proposedBounds.bottom).toInt().coerceIn(0, currentLayer.height)
        if (left >= right || top >= bottom) return false

        val proposal = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        Canvas(proposal).apply {
            translate(-left.toFloat(), -top.toFloat())
            drawProposal(this)
        }

        val proposalPixels = IntArray(proposal.width)
        val currentLayerPixels = IntArray(proposal.width)
        for (row in 0 until proposal.height) {
            proposal.getPixels(proposalPixels, 0, proposal.width, 0, row, proposal.width, 1)
            currentLayer.getPixels(currentLayerPixels, 0, proposal.width, left, top + row, proposal.width, 1)
            if (hasOverlappingVisiblePixels(proposalPixels, currentLayerPixels)) {
                proposal.recycle()
                return true
            }
        }
        proposal.recycle()
        return false
    }

    fun hasOverlappingVisiblePixels(proposalPixels: IntArray, currentLayerPixels: IntArray): Boolean {
        require(proposalPixels.size == currentLayerPixels.size)
        return proposalPixels.indices.any { column ->
            hasAlpha(proposalPixels[column]) && hasAlpha(currentLayerPixels[column])
        }
    }

    private fun hasAlpha(color: Int): Boolean = color ushr 24 != 0
}
