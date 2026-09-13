package de.zwegen.zpaint.tools.implementation

/** Size limits shared by floating text, image and selection boxes. */
class FloatingBoxSizeLimits(private val minimumSize: Float) {
    fun isBelowMinimum(size: Float): Boolean = size < minimumSize

    fun shouldRestoreForResolution(
        width: Float,
        height: Float,
        isResolutionLimited: Boolean,
        maximumResolution: Float
    ): Boolean = isResolutionLimited && maximumResolution > 0f && width * height > maximumResolution

    fun minimumSize(): Float = minimumSize
}
