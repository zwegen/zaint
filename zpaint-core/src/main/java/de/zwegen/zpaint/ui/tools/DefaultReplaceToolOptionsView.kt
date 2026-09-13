package de.zwegen.zpaint.ui.tools

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSeekBar
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.ReplaceToolOptionsView

class DefaultReplaceToolOptionsView(container: ViewGroup) : ReplaceToolOptionsView {
    private val valueInput: AppCompatEditText
    private val seekBar: AppCompatSeekBar
    private val slider: ZaintTopNumberSlider
    private var callback: ReplaceToolOptionsView.Callback? = null

    init {
        val view = LayoutInflater.from(container.context).inflate(R.layout.dialog_zaint_replace_tool, container)
        valueInput = view.findViewById(R.id.zaint_replace_size_value_input)
        seekBar = view.findViewById(R.id.zaint_replace_size_seek_bar)
        slider = ZaintTopNumberSlider(
            input = valueInput,
            seekBar = seekBar,
            onValueChanged = { value, _ -> callback?.setBrushSize(value) },
            onStopTracking = { value -> callback?.setBrushSize(value) }
        )
    }

    override fun setCallback(callback: ReplaceToolOptionsView.Callback) {
        this.callback = callback
    }

    override fun setBrushSizeRange(minimum: Int, maximum: Int, value: Int) {
        valueInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(minimum, maximum))
        slider.setRange(minimum, maximum, value.coerceIn(minimum, maximum))
    }
}
