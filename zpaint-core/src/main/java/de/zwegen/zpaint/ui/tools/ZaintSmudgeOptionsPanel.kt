package de.zwegen.zpaint.ui.tools

import android.graphics.Paint
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.implementation.DEFAULT_DRAG_IN_PERCENT
import de.zwegen.zpaint.tools.implementation.DEFAULT_PRESSURE_IN_PERCENT
import de.zwegen.zpaint.tools.options.ZaintBrushOptions.Listener
import de.zwegen.zpaint.tools.options.ZaintBrushOptions.PreviewState
import de.zwegen.zpaint.tools.options.BrushToolPreview
import de.zwegen.zpaint.tools.options.ZaintSmudgeOptions
import java.util.Locale

private const val MIN_BRUSH_WIDTH = 1
private const val MIN_PERCENT = 1
private const val MAX_PERCENT = 100

class ZaintSmudgeOptionsPanel(container: ViewGroup) : ZaintSmudgeOptions {
    private val widthInput: EditText
    private val widthSlider: SeekBar
    private val capChoices: ChipGroup
    private val roundCap: Chip
    private val squareCap: Chip
    private val preview: BrushToolPreview
    private val pressureInput: EditText
    private val pressureSlider: SeekBar
    private val dragInput: EditText
    private val dragSlider: SeekBar
    private val topOptions: View
    private val bottomOptions: View
    private val widthControl: ZaintTopNumberSlider

    private var brushListener: Listener? = null
    private var smudgeListener: ZaintSmudgeOptions.Listener? = null

    init {
        val panel = LayoutInflater.from(container.context).inflate(
            R.layout.dialog_zpaint_smudge_tool,
            container,
            true
        )
        capChoices = panel.findViewById(R.id.zpaint_stroke_types)
        roundCap = panel.findViewById(R.id.zpaint_stroke_ibtn_circle)
        squareCap = panel.findViewById(R.id.zpaint_stroke_ibtn_rect)
        widthSlider = panel.findViewById(R.id.zpaint_stroke_width_seek_bar)
        widthInput = panel.findViewById(R.id.zpaint_stroke_width_width_text)
        preview = panel.findViewById(R.id.zpaint_brush_tool_preview)
        pressureInput = panel.findViewById(R.id.zpaint_smudge_tool_dialog_pressure_input)
        pressureSlider = panel.findViewById(R.id.zpaint_pressure_seek_bar)
        dragInput = panel.findViewById(R.id.zpaint_smudge_tool_dialog_drag_input)
        dragSlider = panel.findViewById(R.id.zpaint_drag_seek_bar)
        topOptions = panel.findViewById(R.id.zpaint_smudge_top_layout)
        bottomOptions = panel.findViewById(R.id.zpaint_smudge_bottom_layout)

        widthInput.installRange(MIN_BRUSH_WIDTH)
        pressureInput.installRange(MIN_PERCENT)
        dragInput.installRange(MIN_PERCENT)
        widthControl = ZaintTopNumberSlider(
            input = widthInput,
            seekBar = widthSlider,
            onValueChanged = { width, _ ->
                brushListener?.onStrokeWidthSelected(width)
                preview.invalidate()
            },
            parse = { it.toIntOrNull() ?: MIN_BRUSH_WIDTH }
        )
        widthControl.setRange(MIN_BRUSH_WIDTH, MAX_PERCENT, MIN_BRUSH_WIDTH)
        connectPercent(pressureInput, pressureSlider) { smudgeListener?.onPressurePercentSelected(it) }
        connectPercent(dragInput, dragSlider) { smudgeListener?.onDragPercentSelected(it) }
        roundCap.setOnClickListener { selectCap(Paint.Cap.ROUND) }
        squareCap.setOnClickListener { selectCap(Paint.Cap.SQUARE) }
        show(pressureInput, DEFAULT_PRESSURE_IN_PERCENT)
        show(dragInput, DEFAULT_DRAG_IN_PERCENT)
        bottomOptions.visibility = View.GONE
    }

    override fun showPaint(paint: Paint) {
        showStrokeCap(paint.strokeCap)
        val width = paint.strokeWidth.toInt().coerceAtLeast(MIN_BRUSH_WIDTH)
        widthControl.show(width)
    }

    override fun showStrokeCap(cap: Paint.Cap) {
        when (cap) {
            Paint.Cap.ROUND -> capChoices.check(roundCap.id)
            Paint.Cap.SQUARE -> capChoices.check(squareCap.id)
            else -> Unit
        }
    }

    override fun setListener(listener: Listener) {
        brushListener = listener
    }

    override fun setPreviewState(state: PreviewState) {
        preview.setListener(state)
        preview.invalidate()
    }

    override fun hideCapOptions() = Unit

    override fun refreshPreview() {
        preview.invalidate()
    }

    override fun setSmudgeListener(listener: ZaintSmudgeOptions.Listener) {
        smudgeListener = listener
    }

    override fun bottomOptions(): View = bottomOptions

    override fun topOptions(): View = topOptions

    private fun connectPercent(
        input: EditText,
        slider: SeekBar,
        onSelected: (Int) -> Unit
    ) {
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val percent = progress.coerceAtLeast(MIN_PERCENT)
                if (percent != progress) seekBar.progress = percent
                if (fromUser) show(input, percent)
                onSelected(percent)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = show(input, seekBar.progress)
        })
        input.addTextChangedListener(afterTextChanged { value ->
            slider.progress = value.coerceAtLeast(MIN_PERCENT)
        })
    }

    private fun selectCap(cap: Paint.Cap) {
        brushListener?.onCapSelected(cap)
        showStrokeCap(cap)
        preview.invalidate()
    }

    private fun EditText.installRange(minimum: Int) {
        filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(minimum, MAX_PERCENT))
    }

    private fun show(input: EditText, value: Int) {
        input.setText(String.format(Locale.getDefault(), "%d", value))
    }

    private fun afterTextChanged(action: (Int) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable) {
            action(s.toString().toIntOrNull() ?: MIN_PERCENT)
        }
    }
}
