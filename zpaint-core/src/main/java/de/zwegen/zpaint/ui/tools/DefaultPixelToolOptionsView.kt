/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.ui.tools

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSeekBar
import com.google.android.material.button.MaterialButtonToggleGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.PixelToolOptionsView

class DefaultPixelToolOptionsView(toolSpecificOptionsLayout: ViewGroup) : PixelToolOptionsView {
    private val modeToggleGroup: MaterialButtonToggleGroup
    private val seekBar: AppCompatSeekBar
    private val valueInput: AppCompatEditText
    private var callback: PixelToolOptionsView.Callback? = null
    private var minValue = 1
    private var maxValue = 40
    private val topSlider: ZaintTopNumberSlider

    init {
        val inflater = LayoutInflater.from(toolSpecificOptionsLayout.context)
        val pixelToolOptionsView =
            inflater.inflate(R.layout.dialog_zpaint_pixel_tool, toolSpecificOptionsLayout)
        modeToggleGroup = pixelToolOptionsView.findViewById(R.id.zpaint_pixel_mode_toggle_group)
        seekBar = pixelToolOptionsView.findViewById(R.id.zpaint_pixel_size_seek_bar)
        valueInput = pixelToolOptionsView.findViewById(R.id.zpaint_pixel_size_value_input)

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                callback?.setAreaMode(checkedId == R.id.zpaint_pixel_mode_area)
            }
        }

        topSlider = ZaintTopNumberSlider(
            input = valueInput,
            seekBar = seekBar,
            onValueChanged = { value, _ -> callback?.setPixelSize(value) },
            onStopTracking = { value -> callback?.setPixelSize(value) }
        )
    }

    override fun setCallback(callback: PixelToolOptionsView.Callback) {
        this.callback = callback
    }

    override fun setPixelSizeRange(min: Int, max: Int, value: Int) {
        minValue = min
        maxValue = max
        valueInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(min, max))
        val boundedValue = value.coerceIn(min, max)
        topSlider.setRange(min, max, boundedValue)
    }

    override fun setAreaMode(areaMode: Boolean) {
        modeToggleGroup.check(
            if (areaMode) {
                R.id.zpaint_pixel_mode_area
            } else {
                R.id.zpaint_pixel_mode_whole_image
            }
        )
    }

}
