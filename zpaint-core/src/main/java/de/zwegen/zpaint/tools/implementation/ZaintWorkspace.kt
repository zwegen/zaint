package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.ui.Perspective

/** Supplies tools with Zaint's document data and transforms without exposing the activity. */
class ZaintWorkspace(
    override val layerModel: ZaintLayerContracts.Model,
    override var perspective: Perspective,
    private val refreshSurface: () -> Unit
) : Workspace {
    override val width: Int
        get() = layerModel.width
    override val height: Int
        get() = layerModel.height
    override val surfaceWidth: Int
        get() = perspective.surfaceWidth
    override val surfaceHeight: Int
        get() = perspective.surfaceHeight
    override val bitmapOfAllLayers: Bitmap?
        get() = layerModel.getBitmapOfAllLayers()
    override val bitmapListOfAllLayers: List<Bitmap?>
        get() = layerModel.getBitmapListOfAllLayers()
    override val bitmapOfCurrentLayer: Bitmap?
        get() = activeLayerSnapshot()
    override val currentLayerIndex: Int
        get() = layerModel.currentLayer?.let { layerModel.getLayerIndexOf(it) } ?: NO_ACTIVE_LAYER
    override val scaleForCenterBitmap: Float
        get() = perspective.scaleForCenterBitmap
    override var scale: Float
        get() = perspective.scale
        set(value) {
            perspective.scale = value
        }

    override fun contains(point: PointF): Boolean =
        point.x >= 0f && point.x < width && point.y >= 0f && point.y < height

    override fun intersectsWith(rect: RectF): Boolean =
        rect.left < width && rect.top < height && rect.right > 0f && rect.bottom > 0f

    override fun resetPerspective() {
        perspective.setBitmapDimensions(width, height)
        perspective.resetScaleAndTranslation()
    }

    override fun getSurfacePointFromCanvasPoint(canvasPoint: PointF): PointF =
        perspective.getSurfacePointFromCanvasPoint(canvasPoint)

    override fun getCanvasPointFromSurfacePoint(surfacePoint: PointF): PointF =
        perspective.getCanvasPointFromSurfacePoint(surfacePoint)

    override fun invalidate() = refreshSurface()

    private fun activeLayerSnapshot(): Bitmap? {
        val source = layerModel.currentLayer?.bitmap ?: return null
        return source.copy(source.config, false)
    }

    private companion object {
        const val NO_ACTIVE_LAYER = -1
    }
}
