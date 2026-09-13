package de.zwegen.zpaint.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.implementation.PlaceTool
import de.zwegen.zpaint.tools.implementation.ShadowTool

/**
 * The document's ordered layer stack.
 *
 * Index zero is the front-most layer. Rendering walks a stable copy from back to front. Structural
 * changes are synchronized; drawing tools retain ownership of pixel changes in their active layer.
 */
open class ZaintLayerModel : ZaintLayerContracts.Model {
    private val lock = Any()
    private val layerStack = ArrayList<ZaintLayerContracts.ZaintLayer>()

    override var currentLayer: ZaintLayerContracts.ZaintLayer? = null
    override var width: Int = 0
    override var height: Int = 0

    override val layers: List<ZaintLayerContracts.ZaintLayer>
        get() = synchronized(lock) { layerStack.toList() }

    override val layerCount: Int
        get() = synchronized(lock) { layerStack.size }

    override fun reset() {
        synchronized(lock) {
            layerStack.clear()
            currentLayer = null
        }
    }

    override fun getLayerAt(index: Int): ZaintLayerContracts.ZaintLayer? =
        synchronized(lock) { layerStack.getOrNull(index) }

    override fun getLayerIndexOf(layer: ZaintLayerContracts.ZaintLayer): Int =
        synchronized(lock) { layerStack.indexOf(layer) }

    override fun addLayerAt(index: Int, layer: ZaintLayerContracts.ZaintLayer): Boolean = synchronized(lock) {
        if (index !in 0..layerStack.size) false else {
            layerStack.add(index, layer)
            true
        }
    }

    override fun listIterator(index: Int): ListIterator<ZaintLayerContracts.ZaintLayer> = synchronized(lock) {
        layerStack.toList().listIterator(index)
    }

    override fun setLayerAt(position: Int, layer: ZaintLayerContracts.ZaintLayer) {
        synchronized(lock) {
            layerStack[position] = layer
        }
    }

    override fun removeLayerAt(position: Int): Boolean = synchronized(lock) {
        if (position !in layerStack.indices) false else {
            val removedLayer = layerStack.removeAt(position)
            if (currentLayer === removedLayer) {
                currentLayer = layerStack.getOrNull(position) ?: layerStack.lastOrNull()
            }
            true
        }
    }

    override fun getBitmapOfAllLayers(): Bitmap? {
        val orderedLayers = snapshotLayers()
        val referenceBitmap = orderedLayers.firstOrNull()?.bitmap ?: return null
        val mergedBitmap = Bitmap.createBitmap(
            referenceBitmap.width,
            referenceBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        drawLayers(orderedLayers, Canvas(mergedBitmap))
        return mergedBitmap
    }

    override fun getBitmapListOfAllLayers(): List<Bitmap?> = snapshotLayers().map { it.bitmap }

    fun drawLayersOntoCanvas(canvas: Canvas?) {
        if (canvas != null) drawLayers(snapshotLayers(), canvas)
    }

    fun drawLayersOntoCanvasCorrectOrder(
        surfaceViewCanvas: Canvas?,
        currentLayerIndex: Int?,
        drawingBoardCanvas: Canvas?,
        tool: Tool?
    ) {
        val orderedLayers = snapshotLayers()
        for (index in orderedLayers.indices.reversed()) {
            val layer = orderedLayers[index]
            if (!layer.isVisible) continue

            val paint = layerPaint(layer)
            val toolPlacesLayer = tool is PlaceTool && index == currentLayerIndex
            val shadowTool = tool as? ShadowTool
            val shadowPreviewBelowSource = shadowTool?.previewsBelowSourceLayer() == true &&
                index == currentLayerIndex
            if (shadowPreviewBelowSource) {
                surfaceViewCanvas?.let(shadowTool!!::drawPreviewBelowSourceLayer)
                drawingBoardCanvas?.let(shadowTool!!::drawPreviewBelowSourceLayer)
            }
            if (!toolPlacesLayer) {
                surfaceViewCanvas?.drawBitmap(layer.bitmap, 0f, 0f, paint)
                drawingBoardCanvas?.drawBitmap(layer.bitmap, 0f, 0f, paint)
            }
            if (shadowPreviewBelowSource) {
                surfaceViewCanvas?.let(shadowTool!!::drawPreviewOverlay)
                drawingBoardCanvas?.let(shadowTool!!::drawPreviewOverlay)
            } else if (index == currentLayerIndex) {
                surfaceViewCanvas?.let { tool?.draw(it) }
                drawingBoardCanvas?.let { tool?.draw(it) }
            }
        }
    }

    fun drawLayersOntoCanvasCorrectOrderEraser(
        surfaceViewCanvas: Canvas?,
        currentLayerIndex: Int?,
        tool: Tool?
    ) {
        val orderedLayers = snapshotLayers()
        for (index in orderedLayers.indices.reversed()) {
            val layer = orderedLayers[index]
            if (!layer.isVisible) continue

            val paint = layerPaint(layer)
            surfaceViewCanvas?.drawBitmap(layer.bitmap, 0f, 0f, paint)
            if (surfaceViewCanvas != null && index == currentLayerIndex) {
                val workingBitmap = currentLayer?.bitmap ?: continue
                tool?.draw(Canvas(workingBitmap))
                surfaceViewCanvas.drawBitmap(workingBitmap, 0f, 0f, paint)
            }
        }
    }

    private fun snapshotLayers(): List<ZaintLayerContracts.ZaintLayer> = synchronized(lock) {
        layerStack.toList()
    }

    private fun drawLayers(layers: List<ZaintLayerContracts.ZaintLayer>, canvas: Canvas) {
        for (index in layers.indices.reversed()) {
            val layer = layers[index]
            if (layer.isVisible) canvas.drawBitmap(layer.bitmap, 0f, 0f, layerPaint(layer))
        }
    }

    private fun layerPaint(layer: ZaintLayerContracts.ZaintLayer): Paint = Paint().apply {
        isFilterBitmap = false
        alpha = layer.getValueForOpacityPercentage()
    }
}
