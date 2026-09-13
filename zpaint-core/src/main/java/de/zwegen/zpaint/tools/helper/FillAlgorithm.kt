package de.zwegen.zpaint.tools.helper

import android.graphics.Bitmap
import android.graphics.Point

/**
 * Stateful flood-fill operation used by one fill command.
 *
 * Parameters are supplied separately so the command can configure the operation
 * from the current bitmap and then execute it exactly once.
 */
interface FillAlgorithm {
    fun setParameters(
        bitmap: Bitmap,
        clickedPixel: Point,
        targetColor: Int,
        replacementColor: Int,
        colorToleranceThreshold: Float
    )

    fun performFilling()
}
