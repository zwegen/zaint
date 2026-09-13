package de.zwegen.zpaint.tools.common

import android.graphics.MaskFilter
import android.graphics.Paint.Cap
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.options.ZaintBrushOptions.PreviewState

/** Exposes the current tool paint state to a brush-preview widget. */
class CommonBrushPreviewListener(
    private val paintState: ToolPaint,
    override val toolType: ZaintToolKind
) : PreviewState {
    override val strokeWidth: Float
        get() = paintState.strokeWidth

    override val strokeCap: Cap
        get() = paintState.strokeCap

    override val color: Int
        get() = paintState.color

    override val maskFilter: MaskFilter?
        get() = paintState.paint.maskFilter
}
