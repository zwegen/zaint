package de.zwegen.zpaint.ui.tools

import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.implementation.DEFAULT_TOLERANCE_IN_PERCENT
import de.zwegen.zpaint.tools.options.ZaintFillOptions
import java.util.Locale

private const val MIN_TOLERANCE_PERCENT = 0
private const val MAX_TOLERANCE_PERCENT = 100
private const val MIN_GRADIENT_COLORS = 2
private const val MAX_FILL_COLORS = 3

class ZaintFillOptionsPanel(
    optionsContainer: ViewGroup,
    headerContainer: ViewGroup,
    private val openColorPicker: () -> Unit
) : ZaintFillOptions {
    private val toleranceSlider: AppCompatSeekBar
    private val toleranceInput: TextInputEditText
    private val gradientControls: LinearLayout
    private val colorSwatches: List<ImageButton>
    private val linearDirectionButton: ImageButton
    private val waveDirectionButton: ImageButton
    private val radialDirectionButton: ImageButton
    private val imageFillButton: ImageButton
    private val primaryColor: Int
    private val toleranceControl: ZaintTopNumberSlider

    private var listener: ZaintFillOptions.Listener? = null
    private var displayedColors = listOf(Color.BLACK)
    private var selectedDirection = FillGradientDirection.TOP_BOTTOM
    private var linearDirectionIndex = 0
    private var waveDirectionIndex = 0
    private var radialDirection = FillGradientDirection.RADIAL
    private var imageFillSelected = false

    init {
        val inflater = LayoutInflater.from(optionsContainer.context)
        val toleranceControls = inflater.inflate(
            R.layout.dialog_zpaint_fill_tolerance_tool,
            headerContainer
        )
        val fillControls = inflater.inflate(R.layout.dialog_zpaint_fill_tool, optionsContainer)

        toleranceSlider = toleranceControls.findViewById(R.id.zpaint_color_tolerance_seek_bar)
        toleranceInput = toleranceControls.findViewById(
            R.id.zpaint_fill_tool_dialog_color_tolerance_input
        )
        gradientControls = fillControls.findViewById(R.id.zpaint_fill_gradient_controls)
        primaryColor = ContextCompat.getColor(fillControls.context, R.color.zpaint_colorPrimary)
        colorSwatches = listOf(
            fillControls.findViewById(R.id.zpaint_fill_color_0),
            fillControls.findViewById(R.id.zpaint_fill_color_1),
            fillControls.findViewById(R.id.zpaint_fill_color_2)
        )
        linearDirectionButton = fillControls.findViewById(R.id.zpaint_fill_gradient_top_bottom)
        waveDirectionButton = fillControls.findViewById(R.id.zpaint_fill_gradient_wave)
        radialDirectionButton = fillControls.findViewById(R.id.zpaint_fill_gradient_radial)
        imageFillButton = fillControls.findViewById(R.id.zpaint_fill_image)

        toleranceInput.filters = arrayOf(
            InputFilter { source, _, _, destination, dstart, dend ->
                val proposed = destination.toString().replaceRange(dstart, dend, source)
                if (proposed.isEmpty() || proposed.toIntOrNull() in MIN_TOLERANCE_PERCENT..MAX_TOLERANCE_PERCENT) {
                    null
                } else {
                    ""
                }
            },
            DefaultNumberRangeFilter(MIN_TOLERANCE_PERCENT, MAX_TOLERANCE_PERCENT)
        )
        toleranceControl = ZaintTopNumberSlider(
            input = toleranceInput,
            seekBar = toleranceSlider,
            onValueChanged = { value, _ -> listener?.onToleranceSelected(value) }
        )
        toleranceControl.setRange(MIN_TOLERANCE_PERCENT, MAX_TOLERANCE_PERCENT, DEFAULT_TOLERANCE_IN_PERCENT)
        connectPaletteControls()
        showTolerance(DEFAULT_TOLERANCE_IN_PERCENT)
        refreshDirectionButtons()
        refreshPalette()
    }

    override fun setListener(listener: ZaintFillOptions.Listener) {
        this.listener = listener
    }

    override fun renderFillColors(colors: List<Int>) {
        displayedColors = colors.take(MAX_FILL_COLORS).ifEmpty { listOf(Color.BLACK) }
        refreshPalette()
    }

    override fun renderImageFillSelected(selected: Boolean) {
        imageFillSelected = selected
        imageFillButton.isSelected = selected
    }

    private fun connectPaletteControls() {
        linearDirectionButton.setOnClickListener {
            linearDirectionIndex = (linearDirectionIndex + 1) % LINEAR_DIRECTIONS.size
            selectGradientDirection(LINEAR_DIRECTIONS[linearDirectionIndex])
        }
        waveDirectionButton.setOnClickListener {
            waveDirectionIndex = (waveDirectionIndex + 1) % WAVE_DIRECTIONS.size
            selectGradientDirection(WAVE_DIRECTIONS[waveDirectionIndex])
        }
        radialDirectionButton.setOnClickListener {
            deactivateImageFill()
            radialDirection = if (radialDirection == FillGradientDirection.RADIAL) {
                FillGradientDirection.RADIAL_OUTSIDE_IN
            } else {
                FillGradientDirection.RADIAL
            }
            selectGradientDirection(radialDirection)
        }
        colorSwatches.forEachIndexed { index, swatch ->
            swatch.setOnClickListener {
                deactivateImageFill()
                if (index < displayedColors.size) {
                    listener?.onFillColorSelected(index)
                } else {
                    listener?.onAddFillColor()
                }
                openColorPicker()
            }
            swatch.setOnLongClickListener {
                deactivateImageFill()
                if (index < displayedColors.size) {
                    listener?.onRemoveFillColor(index)
                }
                true
            }
        }
        imageFillButton.setOnClickListener {
            if (imageFillSelected) {
                imageFillSelected = false
                imageFillButton.isSelected = false
                listener?.onImageFillDeselected()
            } else {
                listener?.onImageFillSelected()
            }
        }
    }

    private fun showTolerance(percent: Int) {
        toleranceControl.show(percent)
    }

    private fun selectGradientDirection(direction: FillGradientDirection) {
        deactivateImageFill()
        selectedDirection = direction
        refreshDirectionButtons()
        listener?.onGradientDirectionSelected(direction)
    }

    private fun refreshDirectionButtons() {
        linearDirectionButton.drawable.level = linearDirectionIndex * ROTATION_LEVEL_STEP
        waveDirectionButton.drawable.level = waveDirectionIndex * ROTATION_LEVEL_STEP
        radialDirectionButton.setImageResource(
            if (radialDirection == FillGradientDirection.RADIAL) {
                R.drawable.ic_zpaint_gradient_radial_outward
            } else {
                R.drawable.ic_zpaint_gradient_radial_inward
            }
        )
        updateDirectionButton(linearDirectionButton, selectedDirection in LINEAR_DIRECTIONS)
        updateDirectionButton(waveDirectionButton, selectedDirection in WAVE_DIRECTIONS)
        updateDirectionButton(radialDirectionButton, selectedDirection in RADIAL_DIRECTIONS)
    }

    private fun updateDirectionButton(button: ImageButton, selected: Boolean) {
        button.isSelected = selected
        button.backgroundTintList = null
        button.clearColorFilter()
    }

    private fun refreshPalette() {
        gradientControls.visibility = View.VISIBLE
        val gradientEnabled = displayedColors.size >= MIN_GRADIENT_COLORS
        listOf(linearDirectionButton, waveDirectionButton, radialDirectionButton).forEach { button ->
            button.isEnabled = gradientEnabled
            button.alpha = if (gradientEnabled) 1f else 0.35f
        }
        colorSwatches.forEachIndexed { index, swatch ->
            if (index < displayedColors.size) {
                swatch.background = swatchBackground(displayedColors[index], index)
                swatch.setImageDrawable(null)
                swatch.clearColorFilter()
                swatch.contentDescription = swatch.context.getString(R.string.help_content_color_chooser)
            } else {
                swatch.background = emptySwatchBackground(index)
                swatch.setImageResource(R.drawable.ic_zpaint_plus_enabled)
                swatch.setColorFilter(primaryColor)
                swatch.contentDescription = swatch.context.getString(R.string.fill_tool_dialog_add_color)
            }
        }
    }

    private fun deactivateImageFill() {
        if (!imageFillSelected) return
        imageFillSelected = false
        imageFillButton.isSelected = false
        listener?.onImageFillDeselected()
    }

    private fun directionBackground(selected: Boolean): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 0f
        setColor(if (selected) Color.BLACK else Color.WHITE)
        setStroke(dp(1f), if (selected) Color.BLACK else primaryColor)
    }

    private fun swatchBackground(color: Int, index: Int): Drawable {
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = swatchCornerRadii(index)
            setColor(color)
            setStroke(dp(1f), Color.rgb(208, 208, 208))
        }
        return if (Color.alpha(color) == 0) {
            LayerDrawable(
                arrayOf(
                    ContextCompat.getDrawable(gradientControls.context, R.drawable.zpaint_checkeredbg_repeat),
                    border
                )
            )
        } else {
            border
        }
    }

    private fun emptySwatchBackground(index: Int): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadii = swatchCornerRadii(index)
        setColor(Color.WHITE)
        setStroke(dp(1f), Color.rgb(208, 208, 208))
    }

    private fun swatchCornerRadii(index: Int): FloatArray {
        val radius = dp(4f).toFloat()
        return when (index) {
            0 -> floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
            MAX_FILL_COLORS - 1 -> floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            else -> FloatArray(8)
        }
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        gradientControls.resources.displayMetrics
    ).toInt()

    private companion object {
        val LINEAR_DIRECTIONS = listOf(
            FillGradientDirection.TOP_BOTTOM,
            FillGradientDirection.RIGHT_TOP_LEFT_BOTTOM,
            FillGradientDirection.RIGHT_LEFT,
            FillGradientDirection.RIGHT_BOTTOM_LEFT_TOP,
            FillGradientDirection.BOTTOM_TOP,
            FillGradientDirection.LEFT_BOTTOM_RIGHT_TOP,
            FillGradientDirection.LEFT_RIGHT,
            FillGradientDirection.LEFT_TOP_RIGHT_BOTTOM
        )
        val WAVE_DIRECTIONS = listOf(
            FillGradientDirection.RANDOM_WAVE,
            FillGradientDirection.WAVE_RIGHT_TOP_LEFT_BOTTOM,
            FillGradientDirection.WAVE_RIGHT_LEFT,
            FillGradientDirection.WAVE_RIGHT_BOTTOM_LEFT_TOP,
            FillGradientDirection.WAVE_BOTTOM_TOP,
            FillGradientDirection.WAVE_LEFT_BOTTOM_RIGHT_TOP,
            FillGradientDirection.WAVE_LEFT_RIGHT,
            FillGradientDirection.WAVE_LEFT_TOP_RIGHT_BOTTOM
        )
        val RADIAL_DIRECTIONS = listOf(
            FillGradientDirection.RADIAL,
            FillGradientDirection.RADIAL_OUTSIDE_IN
        )
        const val ROTATION_LEVEL_STEP = 1_250
    }
}
