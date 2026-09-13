package de.zwegen.zpaint.tools.options

import android.graphics.MaskFilter
import android.graphics.Paint
import android.view.View
import de.zwegen.zpaint.tools.ZaintToolKind

enum class BrushPreset {
    PENCIL,
    BRUSH,
    MARKER,
    CALLIGRAPHY,
    ARROW
}

interface BrushPresetChangeListener {
    fun changeBrushPreset(brushPreset: BrushPreset)

    fun selectPipette()
}

/** Lets brush-like tools expose a separate softness setting where it makes sense. */
interface BrushSoftnessChangeListener {
    fun changeBrushSoftness(softness: Int)
}

/**
 * Zaint's presentation boundary for brush-like tools.
 */
interface ZaintBrushOptions {
    fun refreshPreview()

    fun showPaint(paint: Paint)

    fun showStrokeCap(cap: Paint.Cap)

    fun showPreset(preset: BrushPreset) = Unit

    fun showPipetteActive(active: Boolean) = Unit

    fun showStrokeWidth(width: Int) = Unit

    fun showBrushSoftness(visible: Boolean, softness: Int = 0) = Unit

    fun setListener(listener: Listener)

    fun setPreviewState(state: PreviewState)

    fun topOptions(): View

    fun bottomOptions(): View

    fun hideCapOptions()

    interface Listener {
        fun onCapSelected(cap: Paint.Cap)

        fun onStrokeWidthSelected(width: Int)

        fun onBrushSoftnessSelected(softness: Int) = Unit

        fun onPresetSelected(preset: BrushPreset) = Unit

        fun onPipetteSelected() = Unit

    }

    interface PreviewState {
        val strokeWidth: Float
        val strokeCap: Paint.Cap
        val color: Int
        val toolType: ZaintToolKind
        val maskFilter: MaskFilter?
    }
}
