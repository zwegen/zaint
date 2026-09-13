package de.zwegen.zpaint.tools.implementation

import android.graphics.Color
import android.graphics.Paint
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.tools.options.ZaintBrushOptions
import de.zwegen.zpaint.ui.viewholder.BottomNavigationViewHolder

/**
 * Zaint's layer-clearing brush. It uses the normal brush interaction model, while keeping a
 * visible neutral preview separate from the transparent stroke committed to the active layer.
 */
class ZaintEraserTool(
    brushOptions: ZaintBrushOptions,
    contextCallback: ContextCallback,
    toolOptions: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    drawTime: Long,
    bottomNavigationViewHolder: BottomNavigationViewHolder? = null
) : ZaintBrushTool(
    brushToolOptionsView = brushOptions,
    contextCallback = contextCallback,
    toolOptionsViewController = toolOptions,
    toolPaint = toolPaint,
    workspace = workspace,
    idlingResource = idlingResource,
    commandManager = commandManager,
    drawTime = drawTime,
    initialBrushPreset = BrushPreset.PENCIL,
    initialBrushStrokeWidths = eraserStrokeWidths()
) {
    init {
        bottomNavigationViewHolder?.setColorButtonColor(Color.TRANSPARENT)
    }

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.ERASER

    override val previewPaint: Paint
        get() = Paint(super.previewPaint).asVisibleEraserPreview()

    override val bitmapPaint: Paint
        get() = Paint(super.bitmapPaint).asLayerClearingStroke(toolPaint.eraseXfermode)

    override val drawPaint: Paint
        get() = Paint(super.drawPaint).apply {
            color = Color.TRANSPARENT
            alpha = 0
            shader = null
            xfermode = null
        }

    private fun Paint.asVisibleEraserPreview() = apply {
        color = PREVIEW_COLOR
        alpha = Color.alpha(PREVIEW_COLOR)
        shader = null
        xfermode = null
        pathEffect = null
        maskFilter = null
        isAntiAlias = false
    }

    private fun Paint.asLayerClearingStroke(eraseXfermode: android.graphics.Xfermode) = apply {
        color = Color.TRANSPARENT
        alpha = 0
        shader = null
        xfermode = eraseXfermode
        pathEffect = null
        maskFilter = null
        isAntiAlias = false
    }

    private companion object {
        const val DEFAULT_WIDTH = 20f
        const val PREVIEW_COLOR = 0xB4505050.toInt()

        fun eraserStrokeWidths(): Map<BrushPreset, Float> =
            ZaintBrushTool.defaultBrushStrokeWidths() + (BrushPreset.PENCIL to DEFAULT_WIDTH)
    }
}
