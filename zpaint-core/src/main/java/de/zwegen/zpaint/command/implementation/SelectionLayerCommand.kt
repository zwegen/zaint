package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.model.ZaintLayer

/** A cutout adds a front layer but never paints into the source. */
internal abstract class SelectionLayerCommand : Command {
    private var sourceIndex = -1
    private var originalVisibility = true
    var applied = false
        private set

    // Only used for the initial interactive submission, never retained for history replay.
    var expectedSource: Bitmap? = null
    var onFirstApplied: (() -> Unit)? = null

    final override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val replayingAppliedEdit = applied
        applied = false
        val source = if (sourceIndex < 0) layerModel.currentLayer else layerModel.getLayerAt(sourceIndex)
        val expected = expectedSource
        expectedSource = null
        source ?: return
        if (expected != null && source.bitmap !== expected) return
        val index = layerModel.getLayerIndexOf(source)
        if (index < 0) return
        val result = createCutout(source.bitmap, layerModel) ?: return
        val layer = ZaintLayer(result)
        if (!result.containsVisiblePixel() || !layerModel.addLayerAt(0, layer)) {
            result.recycle()
            return
        }
        sourceIndex = index
        if (!replayingAppliedEdit) originalVisibility = source.isVisible
        source.isVisible = false
        layerModel.currentLayer = layer
        applied = true
        val notification = onFirstApplied
        onFirstApplied = null
        notification?.invoke()
    }

    // The normal replay restores layer presentation by stack index. Removing this command's
    // inserted layer directly keeps other layers' current visibility and opacity with their pixels.
    fun undo(layerModel: ZaintLayerContracts.Model) {
        if (!applied) return
        layerModel.removeLayerAt(0)
        layerModel.getLayerAt(sourceIndex)?.let { source ->
            source.isVisible = originalVisibility
            layerModel.currentLayer = source
        }
        applied = false
    }

    protected abstract fun createCutout(source: Bitmap, model: ZaintLayerContracts.Model): Bitmap?
    override fun freeResources() = Unit
}

internal fun Bitmap.containsVisiblePixel(): Boolean {
    val row = IntArray(width)
    for (y in 0 until height) {
        getPixels(row, 0, width, 0, y, width, 1)
        if (row.any { it ushr 24 != 0 }) return true
    }
    return false
}

internal class PathSelectionLayerCommand(private val selection: Path) : SelectionLayerCommand() {
    override fun createCutout(source: Bitmap, model: ZaintLayerContracts.Model): Bitmap =
        Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { result ->
            Canvas(result).apply {
                clipPath(selection)
                drawBitmap(source, null, Rect(0, 0, source.width, source.height), null)
            }
        }
}

internal class FloatingSelectionLayerCommand(private val stamp: Command) : SelectionLayerCommand() {
    override fun createCutout(source: Bitmap, model: ZaintLayerContracts.Model): Bitmap =
        Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { result ->
            stamp.run(Canvas(result), model)
        }

    override fun freeResources() = stamp.freeResources()
}
