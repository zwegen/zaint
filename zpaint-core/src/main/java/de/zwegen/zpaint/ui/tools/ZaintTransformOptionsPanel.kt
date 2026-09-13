package de.zwegen.zpaint.ui.tools

import android.text.InputFilter
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSeekBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.ZaintTransformOptions
import de.zwegen.zpaint.tools.options.ZaintTransformOptions.AspectRatio
import java.text.NumberFormat

private const val MIN_PERCENT = 1
private const val MAX_PERCENT = 100

/**
 * Zaint's size, resize and aspect-ratio controls for the adjust tool.
 */
class ZaintTransformOptionsPanel(root: ViewGroup) : ZaintTransformOptions {
    private val widthInput: AppCompatEditText
    private val heightInput: AppCompatEditText
    private val resizeSlider: AppCompatSeekBar
    private val resizePercentInput: AppCompatEditText
    private val ratioButtons: MaterialButtonToggleGroup
    private val freeRatioButton: MaterialButton
    private val squareRatioButton: MaterialButton
    private val fourFiveRatioButton: MaterialButton
    private val sixteenNineRatioButton: MaterialButton
    private val widthWatcher: DimensionWatcher
    private val heightWatcher: DimensionWatcher

    private var listener: ZaintTransformOptions.Listener? = null
    private var aspectRatio = AspectRatio.FREE
    private val resizeControl: ZaintTopNumberSlider

    init {
        val panel = LayoutInflater.from(root.context)
            .inflate(R.layout.dialog_zpaint_transform_tool, root)
        widthInput = panel.findViewById(R.id.zpaint_transform_width_value)
        heightInput = panel.findViewById(R.id.zpaint_transform_height_value)
        resizeSlider = panel.findViewById(R.id.zpaint_transform_resize_seekbar)
        resizePercentInput = panel.findViewById(R.id.zpaint_transform_resize_percentage_text)
        ratioButtons = panel.findViewById(R.id.zpaint_transform_ratio_toggle_group)
        freeRatioButton = panel.findViewById(R.id.zpaint_transform_ratio_free)
        squareRatioButton = panel.findViewById(R.id.zpaint_transform_ratio_square)
        fourFiveRatioButton = panel.findViewById(R.id.zpaint_transform_ratio_four_five)
        sixteenNineRatioButton = panel.findViewById(R.id.zpaint_transform_ratio_sixteen_nine)

        ratioButtons.check(R.id.zpaint_transform_ratio_free)
        widthWatcher = DimensionWatcher { listener?.onWidthChanged(it) }
        heightWatcher = DimensionWatcher { listener?.onHeightChanged(it) }
        widthInput.addTextChangedListener(widthWatcher)
        heightInput.addTextChangedListener(heightWatcher)

        resizePercentInput.filters = arrayOf<InputFilter>(
            DefaultNumberRangeFilter(MIN_PERCENT, MAX_PERCENT)
        )
        resizeControl = ZaintTopNumberSlider(
            input = resizePercentInput,
            seekBar = resizeSlider,
            onValueChanged = { percent, _ -> listener?.onResizePercentChanged(percent) },
            parse = { it.toIntOrNull() ?: MIN_PERCENT }
        )
        resizeControl.setRange(MIN_PERCENT, MAX_PERCENT, resizeSlider.progress.coerceIn(MIN_PERCENT, MAX_PERCENT))

        freeRatioButton.setOnClickListener { selectAspectRatio(AspectRatio.FREE) }
        squareRatioButton.setOnClickListener { selectAspectRatio(AspectRatio.SQUARE) }
        fourFiveRatioButton.setOnClickListener {
            selectAspectRatio(
                if (aspectRatio == AspectRatio.FOUR_FIVE) AspectRatio.FIVE_FOUR else AspectRatio.FOUR_FIVE
            )
        }
        sixteenNineRatioButton.setOnClickListener {
            selectAspectRatio(
                if (aspectRatio == AspectRatio.SIXTEEN_NINE) AspectRatio.NINE_SIXTEEN else AspectRatio.SIXTEEN_NINE
            )
        }
    }

    override fun setWidthRange(filter: NumberRangeFilter) {
        widthInput.filters = arrayOf<InputFilter>(filter)
    }

    override fun setHeightRange(filter: NumberRangeFilter) {
        heightInput.filters = arrayOf<InputFilter>(filter)
    }

    override fun setListener(listener: ZaintTransformOptions.Listener) {
        this.listener = listener
    }

    override fun showWidth(width: Int) {
        updateDimensionInput(widthInput, widthWatcher, width)
    }

    override fun showHeight(height: Int) {
        updateDimensionInput(heightInput, heightWatcher, height)
    }

    override fun showResizePercent(percent: Int) {
        resizeControl.show(percent)
    }

    private fun selectAspectRatio(newAspectRatio: AspectRatio) {
        aspectRatio = newAspectRatio
        updateRatioButtons()
        listener?.onAspectRatioChanged(newAspectRatio)
    }

    private fun updateRatioButtons() {
        when (aspectRatio) {
            AspectRatio.FREE -> {
                ratioButtons.check(R.id.zpaint_transform_ratio_free)
                fourFiveRatioButton.setText(R.string.transform_ratio_four_five)
                sixteenNineRatioButton.setText(R.string.transform_ratio_sixteen_nine)
            }
            AspectRatio.SQUARE -> {
                ratioButtons.check(R.id.zpaint_transform_ratio_square)
                fourFiveRatioButton.setText(R.string.transform_ratio_four_five)
                sixteenNineRatioButton.setText(R.string.transform_ratio_sixteen_nine)
            }
            AspectRatio.FOUR_FIVE -> {
                ratioButtons.check(R.id.zpaint_transform_ratio_four_five)
                fourFiveRatioButton.setText(R.string.transform_ratio_four_five)
            }
            AspectRatio.FIVE_FOUR -> {
                ratioButtons.check(R.id.zpaint_transform_ratio_four_five)
                fourFiveRatioButton.setText(R.string.transform_ratio_five_four)
            }
            AspectRatio.SIXTEEN_NINE -> {
                ratioButtons.check(R.id.zpaint_transform_ratio_sixteen_nine)
                sixteenNineRatioButton.setText(R.string.transform_ratio_sixteen_nine)
            }
            AspectRatio.NINE_SIXTEEN -> {
                ratioButtons.check(R.id.zpaint_transform_ratio_sixteen_nine)
                sixteenNineRatioButton.setText(R.string.transform_ratio_nine_sixteen)
            }
        }
    }

    private fun updateDimensionInput(
        input: AppCompatEditText,
        watcher: DimensionWatcher,
        value: Int
    ) {
        if (input.hasFocus() || input.text.toString() == value.toString()) return
        input.removeTextChangedListener(watcher)
        input.setText(value.toString())
        input.setSelection(input.length())
        input.addTextChangedListener(watcher)
    }

    private class DimensionWatcher(
        private val onDimensionChanged: (Float) -> Unit
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            val text = s.toString().ifEmpty { "1" }
            val value = NumberFormat.getIntegerInstance().parse(text)?.toFloat() ?: return
            onDimensionChanged(value)
        }
    }
}
