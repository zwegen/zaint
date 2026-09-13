package de.zwegen.zpaint.ui.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.text.InputFilter
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButtonToggleGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.implementation.SpeechBubbleType

/** Top stroke-width control and the four fixed speech-bubble choices at the bottom. */
class ZaintSpeechBubbleOptionsPanel(
    topContainer: ViewGroup,
    bottomContainer: ViewGroup
) {
    private var callback: Callback? = null
    private val typeButtons: MaterialButtonToggleGroup
    private val strokeWidthSeekBar: SeekBar
    private val strokeWidthInput: TextInputEditText
    private val typeButtonIds = mutableMapOf<SpeechBubbleType, Int>()
    private val strokeWidthControl: ZaintTopNumberSlider

    init {
        val context = topContainer.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val strokeWidthControls = LayoutInflater.from(context).inflate(
            R.layout.dialog_zpaint_speech_bubble_stroke_width,
            topContainer
        )
        strokeWidthSeekBar = strokeWidthControls.findViewById(R.id.zpaint_speech_bubble_stroke_width_seek_bar)
        strokeWidthInput = strokeWidthControls.findViewById(R.id.zpaint_speech_bubble_stroke_width_input)
        strokeWidthInput.filters = arrayOf(
            InputFilter { source, _, _, destination, dstart, dend ->
                val proposed = destination.toString().replaceRange(dstart, dend, source)
                if (proposed.isEmpty() || proposed.toIntOrNull() in MIN_STROKE_WIDTH..MAX_STROKE_WIDTH) null else ""
            },
            DefaultNumberRangeFilter(MIN_STROKE_WIDTH, MAX_STROKE_WIDTH)
        )
        strokeWidthControl = ZaintTopNumberSlider(
            input = strokeWidthInput,
            seekBar = strokeWidthSeekBar,
            onValueChanged = { value, _ -> callback?.onStrokeWidthChanged(value) }
        )
        strokeWidthControl.setRange(MIN_STROKE_WIDTH, MAX_STROKE_WIDTH, DEFAULT_STROKE_WIDTH)

        LinearLayout(context).apply {
            gravity = Gravity.START
            setPadding(dp(12), 0, dp(12), dp(12))
            typeButtons = LayoutInflater.from(context)
                .inflate(R.layout.zpaint_speech_bubble_type_buttons, this, false) as MaterialButtonToggleGroup
            typeButtonIds.putAll(
                mapOf(
                    SpeechBubbleType.OVAL to R.id.zpaint_speech_bubble_oval,
                    SpeechBubbleType.ROUNDED_RECTANGLE to R.id.zpaint_speech_bubble_rectangle,
                    SpeechBubbleType.CLOUD to R.id.zpaint_speech_bubble_cloud,
                    SpeechBubbleType.THOUGHT to R.id.zpaint_speech_bubble_thought
                )
            )
            typeButtonIds.forEach { (type, id) ->
                typeButtons.findViewById<View>(id).setOnClickListener {
                    typeButtons.check(id)
                    callback?.onTypeSelected(type)
                }
            }
            addView(typeButtons)
        }.also(bottomContainer::addView)
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun setStrokeWidth(width: Int) {
        updateStrokeWidth(width, notify = false)
    }

    fun showSelectedType(type: SpeechBubbleType?) {
        val id = type?.let(typeButtonIds::get) ?: View.NO_ID
        if (id == View.NO_ID) typeButtons.clearChecked() else typeButtons.check(id)
    }

    private fun updateStrokeWidth(width: Int, notify: Boolean) {
        val boundedWidth = width.coerceIn(MIN_STROKE_WIDTH, MAX_STROKE_WIDTH)
        strokeWidthControl.show(boundedWidth)
        if (notify) callback?.onStrokeWidthChanged(boundedWidth)
    }

    interface Callback {
        fun onTypeSelected(type: SpeechBubbleType)
        fun onStrokeWidthChanged(width: Int)
    }

    private companion object {
        const val MIN_STROKE_WIDTH = 1
        const val MAX_STROKE_WIDTH = 20
        const val DEFAULT_STROKE_WIDTH = 4
    }
}
