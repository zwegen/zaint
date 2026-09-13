package de.zwegen.zpaint.colorpicker

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton

/** Standalone HSV/RGBA picker used by Zaint. */
class ColorPickerDialog : AppCompatDialogFragment() {
    private val listeners = mutableListOf<OnColorPickedListener>()
    private var selectedColor = Color.BLACK
    private var onCanvasPipetteRequested: (() -> Unit)? = null

    companion object {
        private const val ARG_COLOR = "color"
        private const val ARG_HISTORY = "history"
        private const val ARG_LOCK_ALPHA = "lock_alpha"
        private const val ARG_PRESERVE_ALPHA_ON_SWATCH_SELECTION =
            "preserve_alpha_on_swatch_selection"

        fun newInstance(
            initialColor: Int,
            colorHistory: ColorHistory = ColorHistory(),
            preserveAlphaOnSwatchSelection: Boolean = false
        ): ColorPickerDialog =
            ColorPickerDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLOR, initialColor)
                    putIntegerArrayList(ARG_HISTORY, colorHistory.colors)
                    putBoolean(ARG_LOCK_ALPHA, false)
                    putBoolean(ARG_PRESERVE_ALPHA_ON_SWATCH_SELECTION, preserveAlphaOnSwatchSelection)
                }
            }
    }

    fun addOnColorPickedListener(listener: OnColorPickedListener) { listeners += listener }
    fun setOnCanvasPipetteRequested(listener: () -> Unit) { onCanvasPipetteRequested = listener }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        selectedColor = savedInstanceState?.getInt(ARG_COLOR) ?: requireArguments().getInt(ARG_COLOR)
        val lockAlpha = requireArguments().getBoolean(ARG_LOCK_ALPHA)
        val preserveAlphaOnSwatchSelection =
            requireArguments().getBoolean(ARG_PRESERVE_ALPHA_ON_SWATCH_SELECTION)
        val history = requireArguments().getIntegerArrayList(ARG_HISTORY).orEmpty()
        val panel = PickerPanel(
            requireContext(),
            selectedColor,
            lockAlpha,
            preserveAlphaOnSwatchSelection,
            history,
            { color -> selectedColor = color },
            { requestCanvasPipette() }
        )
        lateinit var dialog: AlertDialog
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.rgb(238, 238, 238))
            addView(panel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(LinearLayout(context).apply {
                gravity = Gravity.END
                orientation = LinearLayout.HORIZONTAL
                addView(actionButton("Abbrechen", R.drawable.color_picker_cancel_button_bg) { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f))
                addView(actionButton("Übernehmen", R.drawable.color_picker_confirm_button_bg) {
                    listeners.forEach { it.colorChanged(selectedColor) }
                    dialog.dismiss()
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            })
        }
        dialog = AlertDialog.Builder(requireContext())
            .setTitle("Farbe")
            .setView(root)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.color_picker_dialog_background)
            dialog.window?.setDimAmount(0f)
            dialog.window?.decorView?.elevation = dp(12).toFloat()
            dialog.window?.setLayout(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        return dialog
    }

    private fun actionButton(label: String, background: Int, action: () -> Unit): AppCompatButton =
        AppCompatButton(requireContext()).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundResource(background)
            backgroundTintList = null
            minWidth = dp(112)
            minHeight = dp(48)
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestCanvasPipette() {
        onCanvasPipetteRequested?.let { request ->
            dialog?.dismiss()
            request()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { outState.putInt(ARG_COLOR, selectedColor); super.onSaveInstanceState(outState) }
}

private class PickerPanel(
    context: android.content.Context,
    color: Int,
    private val lockAlpha: Boolean,
    private val preserveAlphaOnSwatchSelection: Boolean,
    history: List<Int>,
    private val changed: (Int) -> Unit,
    private val onPipetteRequested: () -> Unit
) : LinearLayout(context) {
    private val hsv = FloatArray(3)
    private var alpha = Color.alpha(color)
    private val preview = TextView(context).apply { gravity = Gravity.CENTER; textSize = 18f; minHeight = 96 }
    private val previewContainer = FrameLayout(context).apply {
        setBackgroundResource(R.drawable.color_picker_checkeredbg_repeat)
        addView(preview, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    private val sliders = mutableListOf<SeekBar>()

    init {
        orientation = VERTICAL
        Color.colorToHSV(color, hsv)
        addView(previewContainer, LayoutParams(LayoutParams.MATCH_PARENT, 110))
        addPresetPalette()
        addHistoryRow(history)
        addSlider("Farbton", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        addSlider("Sättigung", 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        addSlider("Helligkeit", 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }
        if (!lockAlpha) addSlider("Deckkraft", 255, alpha) { alpha = it }
        update()
    }

    private fun addPresetPalette() {
        val colors = intArrayOf(
            0xFF1565C0.toInt(), 0xFF00838F.toInt(), 0xFF2E7D32.toInt(), 0xFF7CB342.toInt(), 0xFFF9A825.toInt(),
            0xFFE65100.toInt(), 0xFFC62828.toInt(), 0xFF6A1B9A.toInt(), 0xFFAD1457.toInt(), 0xFF795548.toInt(),
            0xFF29B6F6.toInt(), Color.BLACK, 0xFF757575.toInt(), 0xFFFFFFFF.toInt(), 0x00000000
        )
        colors.asList().chunked(5).forEach { row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { color -> addView(Button(context).apply {
                    text = ""; contentDescription = String.format("#%08X", color)
                    if (Color.alpha(color) == 0) setBackgroundResource(R.drawable.color_picker_checkeredbg_repeat) else setBackgroundColor(color)
                    setOnClickListener { setColor(color, preserveAlphaOnSwatchSelection) }
                }, LayoutParams(0, 72, 1f).apply { setMargins(2, 2, 2, 2) }) }
            })
        }
    }

    private fun addHistoryRow(colors: List<Int>) {
        val swatches = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = historyContainerBackground()
            repeat(4) { index ->
                val color = colors.getOrNull(index)
                addView(historySwatch(color), LayoutParams(dp(40), dp(40)).apply {
                    if (index > 0) marginStart = dp(4)
                })
            }
            addView(historyPipetteButton(), LayoutParams(dp(40), dp(40)).apply {
                marginStart = dp(4)
            })
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(swatches)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
    }

    private fun historyContainerBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        setStroke(dp(1), Color.rgb(158, 158, 158))
        cornerRadius = dp(8).toFloat()
    }

    private fun historySwatchBackground(color: Int?): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color ?: Color.WHITE)
        setStroke(dp(1), Color.rgb(158, 158, 158))
        cornerRadius = dp(6).toFloat()
    }

    private fun historySwatch(color: Int?): View = FrameLayout(context).apply {
        if (color != null) setBackgroundResource(R.drawable.color_picker_checkeredbg_repeat)
        addView(View(context).apply {
            background = historySwatchBackground(color)
        }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        contentDescription = color?.let { String.format("#%08X", it) } ?: "Leerer letzter Farbplatz"
        if (color != null) {
            isClickable = true
            setOnClickListener { setColor(color, preserveAlphaOnSwatchSelection) }
        }
    }

    private fun historyPipetteButton(): View = FrameLayout(context).apply {
        background = historySwatchBackground(null)
        addView(AppCompatImageButton(context).apply {
            contentDescription = "Farbe aus der Leinwand wählen"
            setImageResource(R.drawable.ic_color_picker_pipette)
            background = null
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { onPipetteRequested() }
        }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun setColor(color: Int, preserveAlpha: Boolean = false) {
        Color.colorToHSV(color, hsv)
        if (!preserveAlpha) alpha = Color.alpha(color)
        sliders[0].progress = hsv[0].toInt()
        sliders[1].progress = (hsv[1] * 100).toInt()
        sliders[2].progress = (hsv[2] * 100).toInt()
        if (!preserveAlpha && !lockAlpha && sliders.size > 3) sliders[3].progress = alpha
        update()
    }
    private fun addSlider(label: String, max: Int, value: Int, set: (Int) -> Unit) {
        addView(TextView(context).apply { text = label })
        addView(SeekBar(context).apply {
            this.max = max
            progress = value
            progressTintList = ColorStateList.valueOf(Color.rgb(32, 201, 151))
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(130, 138, 145))
            thumbTintList = ColorStateList.valueOf(Color.rgb(52, 58, 64))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { set(progress); update() }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }.also { sliders += it })
    }
    private fun update() {
        val color = Color.HSVToColor(alpha, hsv)
        preview.setBackgroundColor(color)
        preview.setTextColor(if (isDark(color)) Color.WHITE else Color.BLACK)
        preview.text = String.format("#%08X", color)
        changed(color)
    }

    private fun isDark(color: Int): Boolean =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 128
}
