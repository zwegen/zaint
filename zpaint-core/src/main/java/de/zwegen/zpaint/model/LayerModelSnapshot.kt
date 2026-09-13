package de.zwegen.zpaint.model

import android.graphics.Bitmap
import de.zwegen.zpaint.contract.ZaintLayerContracts
import java.util.Collections

/**
 * An immutable, bitmap-owning copy of a [ZaintLayerContracts.Model].
 *
 * Create a snapshot while the caller has stable access to the mutable model (normally on the UI
 * thread). Once [capture] returns, this object does not retain any mutable model or layer
 * reference and may be read from a background thread. It contains only private bitmap copies;
 * callers can obtain an additional caller-owned copy through [copyBitmapAt].
 *
 * Ownership rule: the snapshot owns exactly the bitmap copies made by [capture] and releases them
 * through [release]. Call [release] exactly once when the snapshot is no longer needed; repeated
 * calls are safe and do not recycle a bitmap twice. Bitmaps returned by [copyBitmapAt] and created
 * by [restoreInto] belong to their respective callers/models and are never recycled by this class.
 */
class LayerModelSnapshot private constructor(
    private val snapshotWidth: Int,
    private val snapshotHeight: Int,
    private val snapshotCurrentLayerIndex: Int?,
    snapshotLayers: List<OwnedLayer>
) {
    class SnapshotLayer internal constructor(
        val isVisible: Boolean,
        val opacityPercentage: Int
    )

    private class OwnedLayer(
        val metadata: SnapshotLayer,
        val bitmap: Bitmap
    )

    private var layersState: List<OwnedLayer>? = Collections.unmodifiableList(snapshotLayers)

    val width: Int
        get() = synchronized(this) {
            requireAvailable()
            snapshotWidth
        }

    val height: Int
        get() = synchronized(this) {
            requireAvailable()
            snapshotHeight
        }

    val currentLayerIndex: Int?
        get() = synchronized(this) {
            requireAvailable()
            snapshotCurrentLayerIndex
        }

    val layers: List<SnapshotLayer>
        get() = synchronized(this) {
            requireAvailable()
            Collections.unmodifiableList(layersState!!.map(OwnedLayer::metadata))
        }

    val layerCount: Int
        get() = synchronized(this) {
            requireAvailable()
            layersState!!.size
        }

    val isReleased: Boolean
        get() = synchronized(this) { layersState == null }

    /** Exact native allocation currently owned by this snapshot's bitmap copies. */
    fun allocatedByteCount(): Long = synchronized(this) {
        requireAvailable()
        layersState!!.fold(0L) { total, ownedLayer ->
            total.saturatingAdd(ownedLayer.bitmap.allocationByteCount.toLong())
        }
    }

    /**
     * Returns a new caller-owned bitmap copy for background processing. The caller must recycle it
     * when it is no longer needed.
     */
    fun copyBitmapAt(index: Int): Bitmap = synchronized(this) {
        requireAvailable()
        layersState!![index].bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    /** Returns a private snapshot copy that may be handed to a worker independently. */
    fun copy(): LayerModelSnapshot = synchronized(this) {
        requireAvailable()
        LayerModelSnapshot(
            snapshotWidth,
            snapshotHeight,
            snapshotCurrentLayerIndex,
            layersState!!.map { ownedLayer ->
                OwnedLayer(ownedLayer.metadata, ownedLayer.bitmap.copy(Bitmap.Config.ARGB_8888, true))
            }
        )
    }

    /** Creates caller-owned layers for a command which restores this snapshot on the manager thread. */
    fun copyLayers(): List<ZaintLayerContracts.ZaintLayer> = synchronized(this) {
        requireAvailable()
        layersState!!.map { ownedLayer ->
            ZaintLayer(ownedLayer.bitmap.copy(Bitmap.Config.ARGB_8888, true)).apply {
                isVisible = ownedLayer.metadata.isVisible
                opacityPercentage = ownedLayer.metadata.opacityPercentage
            }
        }
    }

    /**
     * Replaces [model]'s layers with independent copies of this snapshot and restores all model
     * state represented by [ZaintLayerContracts.Model]: dimensions, layer order, visibility, opacity,
     * and the selected layer. The supplied model retains ownership of any bitmaps it held before
     * this call; this snapshot never recycles them.
     */
    fun restoreInto(model: ZaintLayerContracts.Model) = synchronized(this) {
        requireAvailable()
        val snapshotLayers = layersState!!
        model.reset()
        model.width = snapshotWidth
        model.height = snapshotHeight
        snapshotLayers.forEachIndexed { index, ownedLayer ->
            val restoredLayer = ZaintLayer(ownedLayer.bitmap.copy(Bitmap.Config.ARGB_8888, true)).apply {
                isVisible = ownedLayer.metadata.isVisible
                opacityPercentage = ownedLayer.metadata.opacityPercentage
            }
            check(model.addLayerAt(index, restoredLayer)) { "Could not restore snapshot layer at index $index" }
        }
        model.currentLayer = snapshotCurrentLayerIndex?.let(model::getLayerAt)
    }

    /**
     * Releases this snapshot's bitmap copies. Returns true only for the call that performed the
     * release. The method is synchronized so concurrent callers cannot recycle a bitmap twice.
     */
    fun release(): Boolean = synchronized(this) {
        val snapshotLayers = layersState ?: return false
        layersState = null
        snapshotLayers.forEach { snapshotLayer ->
            if (!snapshotLayer.bitmap.isRecycled) {
                snapshotLayer.bitmap.recycle()
            }
        }
        true
    }

    private fun requireAvailable() {
        check(layersState != null) { "LayerModelSnapshot has been released" }
    }

    companion object {
        /**
         * Captures [model]'s complete restorable state. The caller must prevent concurrent model
         * mutation while this method runs; after it returns, the result is independent of the UI
         * model and can safely be consumed on a background thread.
         */
        fun capture(model: ZaintLayerContracts.Model): LayerModelSnapshot {
            val modelLayers = model.layers.toList()
            val currentLayerIndex = model.currentLayer?.let(model::getLayerIndexOf)?.takeIf { it >= 0 }
            val copiedLayers = modelLayers.map { layer ->
                OwnedLayer(
                    metadata = SnapshotLayer(
                        isVisible = layer.isVisible,
                        opacityPercentage = layer.opacityPercentage
                    ),
                    bitmap = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                )
            }
            return LayerModelSnapshot(model.width, model.height, currentLayerIndex, copiedLayers)
        }
    }
}

private fun Long.saturatingAdd(other: Long): Long =
    if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other
