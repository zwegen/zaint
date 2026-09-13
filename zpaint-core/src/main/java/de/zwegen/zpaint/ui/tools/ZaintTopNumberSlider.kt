package de.zwegen.zpaint.ui.tools

import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.SeekBar
import java.util.Locale
import kotlin.math.abs

/** Snaps the small neutral area around a centered zero back to zero. */
internal fun snapToNeutralZero(value: Int, minimum: Int, maximum: Int): Int {
    val hasCenteredZero = minimum < 0 && maximum > 0 && minimum == -maximum
    return if (hasCenteredZero && abs(value) <= ZERO_SNAP_THRESHOLD) 0 else value
}

private const val ZERO_SNAP_THRESHOLD = 2
// All number fields deliberately use the same outer width. Negative values such as -100
// still fit inside this width; expanding only symmetric ranges makes their controls jump.
private const val NUMBER_INPUT_EMS = 3
private const val NEGATIVE_NUMBER_HORIZONTAL_PADDING_DP = 1
private const val ZERO_MARKER_WIDTH_DP = 1
private const val ZERO_MARKER_HEIGHT_DP = 8

/**
 * Shared behaviour for the number field and horizontal slider at the top of a tool.
 * Tools keep their own range and callbacks; this class only keeps both controls in sync.
 */
class ZaintTopNumberSlider(
    private val input: EditText,
    private val seekBar: SeekBar,
    private val onValueChanged: (value: Int, source: Source) -> Unit,
    private val onStartTracking: () -> Unit = {},
    private val onStopTracking: (value: Int) -> Unit = {},
    private val normalize: (value: Int) -> Int = { it },
    private val parse: (String) -> Int? = String::toIntOrNull
) {
    enum class Source { SLIDER, INPUT }

    private var minimum = 0
    private var maximum = 100
    private var syncing = false
    private var zeroMarkerAttached = false
    private val zeroMarker = ColorDrawable(input.currentTextColor).apply { alpha = 160 }
    private val defaultPaddingLeft = input.paddingLeft
    private val defaultPaddingTop = input.paddingTop
    private val defaultPaddingRight = input.paddingRight
    private val defaultPaddingBottom = input.paddingBottom

    init {
        seekBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateZeroMarkerPosition()
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                val value = normalized(progress + minimum)
                setValue(value)
                onValueChanged(value, Source.SLIDER)
            }

            override fun onStartTrackingTouch(bar: SeekBar) = onStartTracking()

            override fun onStopTrackingTouch(bar: SeekBar) {
                val value = normalized(bar.progress + minimum)
                setValue(value)
                onStopTracking(value)
            }
        })
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable) {
                if (syncing) return
                val value = parse(s.toString()) ?: return
                if (value !in minimum..maximum) return
                val normalizedValue = normalized(value)
                setValue(normalizedValue)
                onValueChanged(normalizedValue, Source.INPUT)
            }
        })
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) restoreLastValidValue()
        }
        input.setOnEditorActionListener { _, _, _ ->
            restoreLastValidValue()
            false
        }
    }

    fun setRange(minimum: Int, maximum: Int, value: Int) {
        require(minimum <= maximum)
        this.minimum = minimum
        this.maximum = maximum
        input.setEms(NUMBER_INPUT_EMS)
        if (minimum < 0) {
            val compactPadding = (NEGATIVE_NUMBER_HORIZONTAL_PADDING_DP * input.resources.displayMetrics.density).toInt()
            input.setPadding(compactPadding, defaultPaddingTop, compactPadding, defaultPaddingBottom)
        } else {
            input.setPadding(defaultPaddingLeft, defaultPaddingTop, defaultPaddingRight, defaultPaddingBottom)
        }
        seekBar.max = maximum - minimum
        updateZeroMarkerVisibility(minimum < 0 && maximum > 0)
        setValue(value)
    }

    fun show(value: Int) = setValue(value)

    private fun normalized(value: Int): Int = normalize(
        snapToNeutralZero(value.coerceIn(minimum, maximum), minimum, maximum)
    )
        .coerceIn(minimum, maximum)

    private fun setValue(value: Int) {
        val normalizedValue = normalized(value)
        syncing = true
        val progress = normalizedValue - minimum
        if (seekBar.progress != progress) seekBar.progress = progress
        val text = String.format(Locale.getDefault(), "%d", normalizedValue)
        if (input.text.toString() != text) {
            input.setText(text)
            input.setSelection(text.length)
        }
        syncing = false
    }

    private fun restoreLastValidValue() {
        val value = parse(input.text.toString())
        if (value == null || value !in minimum..maximum) {
            setValue(seekBar.progress + minimum)
        }
    }

    private fun updateZeroMarkerVisibility(show: Boolean) {
        if (show && !zeroMarkerAttached) {
            seekBar.overlay.add(zeroMarker)
            zeroMarkerAttached = true
        } else if (!show && zeroMarkerAttached) {
            seekBar.overlay.remove(zeroMarker)
            zeroMarkerAttached = false
        }
        updateZeroMarkerPosition()
    }

    private fun updateZeroMarkerPosition() {
        if (!zeroMarkerAttached || seekBar.width == 0) return
        val density = seekBar.resources.displayMetrics.density
        val markerWidth = (ZERO_MARKER_WIDTH_DP * density).toInt().coerceAtLeast(1)
        val markerHeight = (ZERO_MARKER_HEIGHT_DP * density).toInt().coerceAtLeast(1)
        val trackWidth = seekBar.width - seekBar.paddingLeft - seekBar.paddingRight
        val zeroFraction = -minimum.toFloat() / (maximum - minimum)
        val markerCenter = seekBar.paddingLeft + trackWidth * zeroFraction
        val left = (markerCenter - markerWidth / 2f).toInt()
        val top = (seekBar.height - markerHeight) / 2
        zeroMarker.setBounds(left, top, left + markerWidth, top + markerHeight)
    }
}
