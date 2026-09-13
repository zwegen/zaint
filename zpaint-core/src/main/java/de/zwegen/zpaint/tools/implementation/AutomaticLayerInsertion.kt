package de.zwegen.zpaint.tools.implementation

/**
 * Shared insertion rule for content that may need its own layer.
 *
 * The first document layer is preserved as a background. Later insertions only get a new layer
 * when their visible pixels would cover visible pixels in the active layer.
 */
object AutomaticLayerInsertion {
    fun shouldCreateNewLayer(
        layerCount: Int,
        overlapsVisibleCurrentLayerPixels: () -> Boolean
    ): Boolean = layerCount <= 1 || overlapsVisibleCurrentLayerPixels()
}
