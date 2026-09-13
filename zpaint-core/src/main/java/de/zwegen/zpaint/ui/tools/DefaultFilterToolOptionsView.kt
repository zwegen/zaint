/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.ui.tools

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.InputFilter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.FilterToolOptionsView

class DefaultFilterToolOptionsView(toolSpecificOptionsLayout: ViewGroup) : FilterToolOptionsView {
    companion object {
        private const val MAX_SPLASH_COLORS = 4
    }

    private val title: TextView
    private val seekBar: AppCompatSeekBar
    private val valueInput: AppCompatEditText
    private val zeroMarker: TextView
    private val areaModeToggleGroup: MaterialButtonToggleGroup
    private val areaModeBottomSpacer: View
    private val wholeImageButton: MaterialButton
    private val areaButton: MaterialButton
    private val splashColorPalette: ViewGroup
    private val splashColorSwatches: List<ImageButton>
    private var callback: FilterToolOptionsView.Callback? = null
    private var splashColorListener: FilterToolOptionsView.SplashColorListener? = null
    private var displayedSplashColors: List<Int> = emptyList()
    private var activeSplashColorIndex = 0
    private var minValue = 0
    private var maxValue = 100
    private val topSlider: ZaintTopNumberSlider

    init {
        val inflater = LayoutInflater.from(toolSpecificOptionsLayout.context)
        val filterToolOptionsView =
            inflater.inflate(R.layout.dialog_zpaint_filter_tool, toolSpecificOptionsLayout)
        title = filterToolOptionsView.findViewById(R.id.zpaint_filter_title)
        seekBar = filterToolOptionsView.findViewById(R.id.zpaint_filter_seek_bar)
        valueInput = filterToolOptionsView.findViewById(R.id.zpaint_filter_value_input)
        zeroMarker = filterToolOptionsView.findViewById(R.id.zpaint_filter_zero_marker)
        areaModeToggleGroup = filterToolOptionsView.findViewById(R.id.zpaint_filter_area_mode_toggle_group)
        areaModeBottomSpacer = filterToolOptionsView.findViewById(R.id.zpaint_filter_area_mode_bottom_spacer)
        wholeImageButton = filterToolOptionsView.findViewById(R.id.zpaint_filter_mode_whole_image)
        areaButton = filterToolOptionsView.findViewById(R.id.zpaint_filter_mode_area)
        splashColorPalette = filterToolOptionsView.findViewById(R.id.zpaint_splash_color_palette)
        splashColorSwatches = listOf(
            filterToolOptionsView.findViewById(R.id.zpaint_splash_color_0),
            filterToolOptionsView.findViewById(R.id.zpaint_splash_color_1),
            filterToolOptionsView.findViewById(R.id.zpaint_splash_color_2),
            filterToolOptionsView.findViewById(R.id.zpaint_splash_color_3)
        )
        topSlider = ZaintTopNumberSlider(
            input = valueInput,
            seekBar = seekBar,
            onValueChanged = { value, source ->
                callback?.onValueChanged(value)
                if (source == ZaintTopNumberSlider.Source.INPUT) callback?.onStopTracking(value)
            },
            onStartTracking = { callback?.onStartTracking() },
            onStopTracking = { value -> callback?.onStopTracking(value) },
        )
        wholeImageButton.setOnClickListener { callback?.setAreaMode(false) }
        areaButton.setOnClickListener { callback?.setAreaMode(true) }
        splashColorSwatches.forEachIndexed { index, swatch ->
            swatch.setOnClickListener {
                if (index < displayedSplashColors.size) {
                    splashColorListener?.onSplashColorSelected(index)
                } else {
                    splashColorListener?.onAddSplashColor()
                }
            }
            swatch.setOnLongClickListener {
                if (index < displayedSplashColors.size) {
                    splashColorListener?.onRemoveSplashColor(index)
                }
                true
            }
        }
    }

    override fun setCallback(callback: FilterToolOptionsView.Callback) {
        this.callback = callback
    }

    override fun setTitle(titleResource: Int) {
        title.setText(titleResource)
    }

    override fun setRange(min: Int, max: Int, value: Int) {
        minValue = min
        maxValue = max
        valueInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(min, max))
        zeroMarker.visibility = if (isTwoDirectionRange()) View.VISIBLE else View.GONE
        val boundedValue = value.coerceIn(min, max)
        topSlider.setRange(min, max, boundedValue)
    }

    override fun setAreaModeVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        areaModeToggleGroup.visibility = visibility
        areaModeBottomSpacer.visibility = visibility
    }

    override fun setAreaMode(areaMode: Boolean) {
        areaModeToggleGroup.check(if (areaMode) areaButton.id else wholeImageButton.id)
    }

    override fun setSplashColors(colors: List<Int>, activeIndex: Int) {
        displayedSplashColors = colors.take(MAX_SPLASH_COLORS).ifEmpty { listOf(Color.BLACK) }
        activeSplashColorIndex = activeIndex.coerceIn(displayedSplashColors.indices)
        splashColorPalette.visibility = View.VISIBLE
        splashColorSwatches.forEachIndexed { index, swatch ->
            if (index < displayedSplashColors.size) {
                val color = displayedSplashColors[index]
                val active = index == activeSplashColorIndex
                swatch.background = splashSwatchBackground(color, index)
                if (active) {
                    val iconPadding = dp(12f)
                    swatch.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                    swatch.setImageResource(R.drawable.ic_zpaint_tool_pipette)
                    swatch.setColorFilter(contrastingFrameColor(color))
                } else {
                    swatch.setPadding(0, 0, 0, 0)
                    swatch.setImageDrawable(null)
                    swatch.clearColorFilter()
                }
                swatch.contentDescription = swatch.context.getString(R.string.help_content_color_chooser)
            } else {
                swatch.background = emptySplashSwatchBackground(index)
                swatch.setPadding(0, 0, 0, 0)
                swatch.setImageResource(R.drawable.ic_zpaint_plus_enabled)
                swatch.setColorFilter(Color.BLACK)
                swatch.contentDescription = swatch.context.getString(R.string.fill_tool_dialog_add_color)
            }
        }
    }

    override fun setSplashColorListener(listener: FilterToolOptionsView.SplashColorListener) {
        splashColorListener = listener
    }

    private fun isTwoDirectionRange(): Boolean = minValue < 0 && maxValue > 0

    private fun splashSwatchBackground(color: Int, index: Int): Drawable {
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = splashSwatchCornerRadii(index)
            setColor(color)
            setStroke(dp(1f), Color.rgb(208, 208, 208))
        }
        return if (Color.alpha(color) == 0) {
            LayerDrawable(
                arrayOf(
                    ContextCompat.getDrawable(splashColorPalette.context, R.drawable.zpaint_checkeredbg_repeat),
                    border
                )
            )
        } else {
            border
        }
    }

    private fun emptySplashSwatchBackground(index: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadii = splashSwatchCornerRadii(index)
        setColor(Color.WHITE)
        setStroke(dp(1f), Color.rgb(208, 208, 208))
    }

    private fun splashSwatchCornerRadii(index: Int): FloatArray {
        val radius = dp(4f).toFloat()
        return when (index) {
            0 -> floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
            MAX_SPLASH_COLORS - 1 -> floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            else -> FloatArray(8)
        }
    }

    private fun contrastingFrameColor(color: Int): Int {
        val brightness = Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f
        return if (brightness < 128f) Color.WHITE else Color.BLACK
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        splashColorPalette.resources.displayMetrics
    ).toInt()

}
