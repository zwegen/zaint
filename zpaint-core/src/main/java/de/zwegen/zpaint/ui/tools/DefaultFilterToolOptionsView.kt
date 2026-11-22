/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.ui.tools

import android.graphics.Color
import android.text.InputFilter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSeekBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.FilterRegion
import de.zwegen.zpaint.tools.options.FilterToolOptionsView

class DefaultFilterToolOptionsView(toolSpecificOptionsLayout: ViewGroup) : FilterToolOptionsView {
    companion object {
        private const val MAX_SPLASH_COLORS = 4
    }

    private val title: TextView
    private val seekBar: AppCompatSeekBar
    private val valueInput: AppCompatEditText
    private val neutralTick: View
    private val areaModeToggleGroup: MaterialButtonToggleGroup
    private val areaModeBottomSpacer: View
    private val wholeImageButton: MaterialButton
    private val areaButton: MaterialButton
    private val autoButton: MaterialButton
    private val autoHint: TextView
    private val splashColorPalette: ViewGroup
    private val splashColorSwatches: List<ImageButton>
    private var callback: FilterToolOptionsView.Callback? = null
    private var splashColorListener: FilterToolOptionsView.SplashColorListener? = null
    private var displayedSplashColors: List<Int> = emptyList()
    private var activeSplashColorIndex = 0
    private val topSlider: ZaintTopNumberSlider

    init {
        val inflater = LayoutInflater.from(toolSpecificOptionsLayout.context)
        val filterToolOptionsView =
            inflater.inflate(R.layout.dialog_zpaint_filter_tool, toolSpecificOptionsLayout)
        title = filterToolOptionsView.findViewById(R.id.zpaint_filter_title)
        seekBar = filterToolOptionsView.findViewById(R.id.zpaint_filter_seek_bar)
        valueInput = filterToolOptionsView.findViewById(R.id.zpaint_filter_value_input)
        neutralTick = filterToolOptionsView.findViewById(R.id.zpaint_filter_neutral_tick)
        areaModeToggleGroup = filterToolOptionsView.findViewById(R.id.zpaint_filter_area_mode_toggle_group)
        areaModeBottomSpacer = filterToolOptionsView.findViewById(R.id.zpaint_filter_area_mode_bottom_spacer)
        wholeImageButton = filterToolOptionsView.findViewById(R.id.zpaint_filter_mode_whole_image)
        areaButton = filterToolOptionsView.findViewById(R.id.zpaint_filter_mode_area)
        autoButton = filterToolOptionsView.findViewById(R.id.zpaint_filter_mode_auto)
        autoHint = filterToolOptionsView.findViewById(R.id.zpaint_filter_auto_hint)
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
            showZeroOverlay = false,
            onValueChanged = { value, source ->
                callback?.onValueChanged(value)
                if (source == ZaintTopNumberSlider.Source.INPUT) callback?.onStopTracking(value)
            },
            onStartTracking = { callback?.onStartTracking() },
            onStopTracking = { value -> callback?.onStopTracking(value) },
        )
        wholeImageButton.setOnClickListener { callback?.setRegion(FilterRegion.TOTAL) }
        areaButton.setOnClickListener { callback?.setRegion(FilterRegion.AREA) }
        autoButton.setOnClickListener { callback?.setRegion(FilterRegion.AUTO) }
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
        valueInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(min, max))
        neutralTick.visibility = if (min < 0 && min == -max) View.VISIBLE else View.GONE
        val boundedValue = value.coerceIn(min, max)
        topSlider.setRange(min, max, boundedValue)
    }

    override fun setAreaModeVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        areaModeToggleGroup.visibility = visibility
        areaModeBottomSpacer.visibility = visibility
        updateAutoHintVisibility()
    }

    override fun setRegion(region: FilterRegion) {
        val button = when (region) {
            FilterRegion.TOTAL -> wholeImageButton
            FilterRegion.AREA -> areaButton
            FilterRegion.AUTO -> autoButton
        }
        areaModeToggleGroup.check(button.id)
        updateAutoHintVisibility()
    }

    override fun setAutoHint(@StringRes hint: Int) {
        autoHint.setText(hint)
    }

    private fun updateAutoHintVisibility() {
        val showHint = areaModeToggleGroup.visibility == View.VISIBLE && autoButton.isChecked
        autoHint.visibility = if (showHint) View.VISIBLE else View.GONE
    }

    override fun setSplashColors(colors: List<Int>, activeIndex: Int) {
        displayedSplashColors = colors.take(MAX_SPLASH_COLORS).ifEmpty { listOf(Color.BLACK) }
        activeSplashColorIndex = activeIndex.coerceIn(displayedSplashColors.indices)
        splashColorPalette.visibility = View.VISIBLE
        splashColorSwatches.forEachIndexed { index, swatch ->
            if (index < displayedSplashColors.size) {
                val color = displayedSplashColors[index]
                val active = index == activeSplashColorIndex
                swatch.background = GroupedSwatchBackground.color(splashColorPalette.context, color, index, MAX_SPLASH_COLORS - 1)
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
                swatch.background = GroupedSwatchBackground.empty(splashColorPalette.context, index, MAX_SPLASH_COLORS - 1)
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
