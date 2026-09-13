package de.zwegen.zpaint.tools

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.ui.Perspective

/** Read/write view of the Zaint document and the viewport used by a tool. */
interface Workspace {
    val width: Int
    val height: Int
    val surfaceWidth: Int
    val surfaceHeight: Int
    val bitmapOfAllLayers: Bitmap?
    val bitmapListOfAllLayers: List<Bitmap?>
    val bitmapOfCurrentLayer: Bitmap?
    val currentLayerIndex: Int
    val scaleForCenterBitmap: Float
    var scale: Float
    var perspective: Perspective
    val layerModel: ZaintLayerContracts.Model

    fun contains(point: PointF): Boolean
    fun intersectsWith(rect: RectF): Boolean
    fun resetPerspective()
    fun getSurfacePointFromCanvasPoint(canvasPoint: PointF): PointF
    fun getCanvasPointFromSurfacePoint(surfacePoint: PointF): PointF
    fun invalidate()
}
