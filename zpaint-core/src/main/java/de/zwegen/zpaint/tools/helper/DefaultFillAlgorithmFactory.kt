package de.zwegen.zpaint.tools.helper

/**
 * Supplies the bitmap flood-fill implementation selected by Zaint's command
 * layer. A new instance is required for every fill command because it keeps
 * operation-specific parameters.
 */
class DefaultFillAlgorithmFactory : FillAlgorithmFactory {
    override fun createFillAlgorithm(): FillAlgorithm {
        return ZaintScanlineFill()
    }
}
