package de.zwegen.zpaint.ui.tools

import android.graphics.Paint
import android.content.res.ColorStateList
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.core.content.ContextCompat
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.tools.options.ZaintBrushOptions
import de.zwegen.zpaint.tools.options.BrushToolPreview

private const val MIN_WIDTH = 1
private const val MAX_WIDTH = 100

/**
 * Existing Zaint brush controls: presets, pipette, cap, width and preview.
 */
class ZaintBrushOptionsPanel(
    container: ViewGroup,
    private val showPresets: Boolean = false,
    private val showCaps: Boolean = true,
    private val showPreview: Boolean = true,
    private val onLineSelected: () -> Unit = {},
    private val onBrushPresetSelected: ((BrushPreset) -> Unit)? = null,
    private val onPipetteSelected: (() -> Unit)? = null
) : ZaintBrushOptions {
    private val widthInput: EditText
    private val widthSlider: SeekBar
    private val softnessInput: EditText
    private val softnessSlider: SeekBar
    private val capButtons: MaterialButtonToggleGroup
    private val presetButtons: MaterialButtonToggleGroup
    private val presetRow: View
    private val roundCap: MaterialButton
    private val squareCap: MaterialButton
    private val pencilPreset: MaterialButton
    private val brushPreset: MaterialButton
    private val markerPreset: MaterialButton
    private val calligraphyPreset: MaterialButton
    private val arrowPreset: MaterialButton
    private val linePreset: MaterialButton
    private val pipetteButton: MaterialButton
    private val preview: BrushToolPreview
    private val previewArea: View
    private val topArea: View
    private val bottomArea: View
    private val softnessArea: View

    private var listener: ZaintBrushOptions.Listener? = null
    private val widthControl: ZaintTopNumberSlider
    private val softnessControl: ZaintTopNumberSlider

    init {
        val panel = LayoutInflater.from(container.context)
            .inflate(R.layout.dialog_zpaint_stroke, container, true)
        capButtons = panel.findViewById(R.id.zpaint_stroke_types)
        presetRow = panel.findViewById(R.id.zpaint_brush_preset_row)
        presetButtons = panel.findViewById(R.id.zpaint_brush_preset_types)
        roundCap = panel.findViewById(R.id.zpaint_stroke_ibtn_circle)
        squareCap = panel.findViewById(R.id.zpaint_stroke_ibtn_rect)
        pencilPreset = panel.findViewById(R.id.zpaint_brush_preset_pencil)
        brushPreset = panel.findViewById(R.id.zpaint_brush_preset_brush)
        markerPreset = panel.findViewById(R.id.zpaint_brush_preset_marker)
        calligraphyPreset = panel.findViewById(R.id.zpaint_brush_preset_calligraphy)
        arrowPreset = panel.findViewById(R.id.zpaint_brush_preset_arrow)
        linePreset = panel.findViewById(R.id.zpaint_brush_preset_line)
        pipetteButton = panel.findViewById(R.id.zpaint_brush_pipette)
        widthSlider = panel.findViewById(R.id.zpaint_stroke_width_seek_bar)
        widthInput = panel.findViewById(R.id.zpaint_stroke_width_width_text)
        softnessSlider = panel.findViewById(R.id.zpaint_stroke_softness_seek_bar)
        softnessInput = panel.findViewById(R.id.zpaint_stroke_softness_text)
        preview = panel.findViewById(R.id.zpaint_brush_tool_preview)
        previewArea = panel.findViewById(R.id.zpaint_brush_preview_layout)
        topArea = panel.findViewById(R.id.zpaint_stroke_top_layout)
        bottomArea = panel.findViewById(R.id.zpaint_stroke_bottom_layout)
        softnessArea = panel.findViewById(R.id.zpaint_stroke_softness_layout)

        widthInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(MIN_WIDTH, MAX_WIDTH))
        softnessInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(0, MAX_SOFTNESS))
        configureVisibility()
        widthControl = ZaintTopNumberSlider(
            input = widthInput,
            seekBar = widthSlider,
            onValueChanged = { width, _ ->
                listener?.onStrokeWidthSelected(width)
                preview.invalidate()
            }
        )
        widthControl.setRange(MIN_WIDTH, MAX_WIDTH, MIN_WIDTH)
        softnessControl = ZaintTopNumberSlider(
            input = softnessInput,
            seekBar = softnessSlider,
            onValueChanged = { softness, _ ->
                listener?.onBrushSoftnessSelected(softness)
            }
        )
        softnessControl.setRange(0, MAX_SOFTNESS, 0)
        val softnessColor = ContextCompat.getColor(panel.context, R.color.zpaint_colorBrushSoftnessAccent)
        softnessSlider.progressTintList = ColorStateList.valueOf(softnessColor)
        softnessSlider.thumbTintList = ColorStateList.valueOf(softnessColor)
        roundCap.setOnClickListener { selectCap(Paint.Cap.ROUND) }
        squareCap.setOnClickListener { selectCap(Paint.Cap.SQUARE) }
        pencilPreset.setOnClickListener { selectPreset(BrushPreset.PENCIL) }
        brushPreset.setOnClickListener { selectPreset(BrushPreset.BRUSH) }
        markerPreset.setOnClickListener { selectPreset(BrushPreset.MARKER) }
        calligraphyPreset.setOnClickListener { selectPreset(BrushPreset.CALLIGRAPHY) }
        arrowPreset.setOnClickListener { selectPreset(BrushPreset.ARROW) }
        linePreset.setOnClickListener { onLineSelected() }
        pipetteButton.setOnClickListener { onPipetteSelected?.invoke() ?: listener?.onPipetteSelected() }
    }

    override fun refreshPreview() = preview.invalidate()

    override fun showPaint(paint: Paint) {
        showStrokeCap(paint.strokeCap)
        showStrokeWidth(paint.strokeWidth.toInt())
    }

    override fun showStrokeCap(cap: Paint.Cap) {
        when (cap) {
            Paint.Cap.ROUND -> capButtons.check(roundCap.id)
            Paint.Cap.SQUARE -> capButtons.check(squareCap.id)
            else -> Unit
        }
    }

    override fun showPreset(preset: BrushPreset) {
        if (!showPresets) return
        presetButtons.check(
            when (preset) {
                BrushPreset.PENCIL -> pencilPreset.id
                BrushPreset.BRUSH -> pencilPreset.id
                BrushPreset.MARKER -> markerPreset.id
                BrushPreset.CALLIGRAPHY -> calligraphyPreset.id
                BrushPreset.ARROW -> arrowPreset.id
            }
        )
    }

    override fun showPipetteActive(active: Boolean) {
        if (showPresets) pipetteButton.isChecked = active
    }

    override fun showStrokeWidth(width: Int) {
        val visibleWidth = width.coerceIn(MIN_WIDTH, MAX_WIDTH)
        widthControl.show(visibleWidth)
        preview.invalidate()
    }

    override fun showBrushSoftness(visible: Boolean, softness: Int) {
        softnessArea.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) softnessControl.show(softness.coerceIn(0, MAX_SOFTNESS))
    }

    override fun setListener(listener: ZaintBrushOptions.Listener) {
        this.listener = listener
    }

    override fun setPreviewState(state: ZaintBrushOptions.PreviewState) {
        preview.setListener(state)
        preview.invalidate()
    }

    override fun topOptions(): View = topArea

    override fun bottomOptions(): View = bottomArea

    override fun hideCapOptions() {
        capButtons.visibility = View.GONE
        presetButtons.visibility = View.GONE
    }

    private fun configureVisibility() {
        topArea.visibility = View.VISIBLE
        presetRow.visibility = if (showPresets) View.VISIBLE else View.GONE
        presetButtons.visibility = if (showPresets) View.VISIBLE else View.GONE
        capButtons.visibility = if (!showPresets && showCaps) View.VISIBLE else View.GONE
        previewArea.visibility = if (!showPresets && showPreview) View.VISIBLE else View.GONE
        bottomArea.visibility =
            if (!showPresets && !showCaps && !showPreview) View.INVISIBLE else View.VISIBLE
        softnessArea.visibility = View.GONE
    }

    private fun selectCap(cap: Paint.Cap) {
        listener?.onCapSelected(cap)
        showStrokeCap(cap)
        preview.invalidate()
    }

    private fun selectPreset(preset: BrushPreset) {
        onBrushPresetSelected?.invoke(preset) ?: listener?.onPresetSelected(preset)
    }

    private companion object {
        const val MAX_SOFTNESS = 100
    }

}
