package de.zwegen.zpaint.tools.common

import android.graphics.Paint
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.tools.options.BrushPresetChangeListener
import de.zwegen.zpaint.tools.options.BrushSoftnessChangeListener
import de.zwegen.zpaint.tools.options.ZaintBrushOptions.Listener

/** Connects Zaint's shared brush controls to the currently active tool. */
class ZaintBrushOptionsListener(private val target: Tool) : Listener {
    override fun onCapSelected(cap: Paint.Cap) {
        target.changePaintStrokeCap(cap)
    }

    override fun onStrokeWidthSelected(width: Int) {
        target.changePaintStrokeWidth(width)
    }

    override fun onBrushSoftnessSelected(softness: Int) {
        (target as? BrushSoftnessChangeListener)?.changeBrushSoftness(softness)
    }

    override fun onPresetSelected(preset: BrushPreset) {
        brushPresetTarget()?.changeBrushPreset(preset)
    }

    override fun onPipetteSelected() {
        brushPresetTarget()?.selectPipette()
    }

    private fun brushPresetTarget(): BrushPresetChangeListener? =
        target as? BrushPresetChangeListener
}
