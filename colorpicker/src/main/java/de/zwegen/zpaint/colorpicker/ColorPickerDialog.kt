package de.zwegen.zpaint.colorpicker

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

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
                addView(actionButton(getString(R.string.color_picker_cancel), R.drawable.color_picker_cancel_button_bg) { dialog.dismiss() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(actionButton(getString(R.string.color_picker_apply), R.drawable.color_picker_confirm_button_bg) {
                    if (panel.confirmHexInput()) {
                        listeners.forEach { it.colorChanged(selectedColor) }
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            })
        }
        dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.color_picker_title)
            .setView(ScrollView(requireContext()).apply { addView(root) })
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
            minWidth = 0
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
    // Draft text is deliberately independent of the selected color until explicit confirmation.
    private var editingHex = false
    private var confirmedHex = ""
    private val preview = EditText(context).apply {
        gravity = Gravity.CENTER
        textSize = 18f
        typeface = Typeface.DEFAULT
        minWidth = 0
        minHeight = resources.getDimensionPixelSize(R.dimen.color_picker_preview_min_height)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // Keep the existing label's wrapping at large font sizes without a multiline IME.
        setHorizontallyScrolling(false)
        imeOptions = EditorInfo.IME_ACTION_DONE
        setPadding(dp(8), dp(16), dp(8), dp(16))
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setSelectAllOnFocus(true)
        showSoftInputOnFocus = false
        contentDescription = context.getString(R.string.color_picker_edit_hex)
        setOnFocusChangeListener { _, hasFocus -> if (hasFocus) editingHex = true }
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                editingHex = true
                showSoftInputOnFocus = true
            }
            false
        }
        setOnClickListener { beginHexEditing() }
        setOnEditorActionListener { _, action, event ->
            when {
                action == EditorInfo.IME_ACTION_DONE -> { confirmHexInput(); true }
                event?.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) confirmHexInput()
                    true
                }
                else -> false
            }
        }
        ViewCompat.replaceAccessibilityAction(this,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.color_picker_edit_hex)) { _, _ -> beginHexEditing(); true }
    }
    private val copyIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_color_picker_copy)
        setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Hexcode", confirmedHex))
        }
    }
    private val previewContainer = FrameLayout(context).apply {
        setBackgroundResource(R.drawable.color_picker_checkeredbg_repeat)
        addView(preview, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(copyIcon, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(6)
            marginEnd = dp(6)
        })
    }
    private val sliders = mutableListOf<SeekBar>()

    init {
        orientation = VERTICAL
        // The inline field must not take initial focus or summon the keyboard on opening.
        isFocusableInTouchMode = true
        requestFocus()
        Color.colorToHSV(color, hsv)
        addView(previewContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addPresetPalette()
        addHistoryRow(history)
        addSlider(context.getString(R.string.color_picker_hue), 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        addSlider(context.getString(R.string.color_picker_saturation), 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        addSlider(context.getString(R.string.color_picker_brightness), 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }
        if (!lockAlpha) addSlider(context.getString(R.string.color_picker_opacity), 255, alpha) { alpha = it }
        update()
    }

    private fun beginHexEditing() {
        editingHex = true
        preview.showSoftInputOnFocus = true
        preview.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(preview, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun finishHexEditing() {
        val wasEditing = editingHex || preview.hasFocus()
        editingHex = false
        preview.error = null
        preview.showSoftInputOnFocus = false
        if (preview.hasFocus()) {
            preview.clearFocus()
            requestFocus()
        }
        if (wasEditing) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(preview.windowToken, 0)
        }
    }

    /** Done and the enclosing Apply are the only ways to accept typed text. */
    fun confirmHexInput(): Boolean {
        val text = preview.text.toString()
        val color = parseHexColor(text)
        if (color == null) {
            editingHex = true
            preview.requestFocus()
            preview.error = context.getString(R.string.color_picker_invalid_hex)
            return false
        }
        if (text != confirmedHex) {
            setColor(color)
        } else {
            finishHexEditing()
        }
        return true
    }

    private fun addPresetPalette() {
        val colors = intArrayOf(
            0xFF1565C0.toInt(), 0xFF00838F.toInt(), 0xFF2E7D32.toInt(), 0xFF7CB342.toInt(), 0xFFF9A825.toInt(),
            0xFFE65100.toInt(), 0xFFC62828.toInt(), 0xFF6A1B9A.toInt(), 0xFFAD1457.toInt(), 0xFF795548.toInt(),
            0xFF29B6F6.toInt(), Color.BLACK, 0xFF757575.toInt(), 0xFFFFFFFF.toInt(), 0x00000000
        )
        val swatches = colors.map { color -> Button(context).apply {
            text = ""
            contentDescription = String.format("#%08X", color)
            if (Color.alpha(color) == 0) setBackgroundResource(R.drawable.color_picker_checkeredbg_repeat)
            else setBackgroundColor(color)
            setOnClickListener { setColor(color, preserveAlphaOnSwatchSelection) }
        } }
        addView(SwatchRows(context, swatches, 5),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = swatchGap() })
    }

    private fun addHistoryRow(colors: List<Int>) {
        val swatches = (0 until ColorHistory.MAX_ENTRIES).map { historySwatch(colors.getOrNull(it)) } +
            historyPipetteButton()
        addView(SwatchRows(context, swatches, ColorHistory.MAX_ENTRIES + 1),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = resources.getDimensionPixelSize(R.dimen.color_picker_history_top_gap) })
    }

    private fun historySwatchBackground(color: Int?): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color ?: Color.WHITE)
        setStroke(dp(1), Color.rgb(158, 158, 158))
        cornerRadius = dp(6).toFloat()
    }

    private fun historySwatch(color: Int?): View = FrameLayout(context).apply {
        addView(FrameLayout(context).apply {
            if (color != null) setBackgroundResource(R.drawable.color_picker_checkeredbg_repeat)
            addView(View(context).apply { background = historySwatchBackground(color) },
                FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, historyArtworkSize(), Gravity.TOP))
        contentDescription = color?.let { String.format("#%08X", it) } ?: context.getString(R.string.color_picker_empty_history)
        if (color != null) {
            isClickable = true
            setOnClickListener { setColor(color, preserveAlphaOnSwatchSelection) }
        }
    }

    private fun swatchGap() = resources.getDimensionPixelSize(R.dimen.color_picker_swatch_gap)

    private fun historyArtworkSize() = resources.getDimensionPixelSize(R.dimen.color_picker_history_artwork)

    private fun historyPipetteButton(): View = FrameLayout(context).apply {
        // Fill the column width; retain the 40dp frame height and 48dp touch target.
        addView(View(context).apply { background = historySwatchBackground(null) },
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, historyArtworkSize(), Gravity.TOP))
        addView(AppCompatImageButton(context).apply {
            contentDescription = context.getString(R.string.color_picker_canvas_pipette)
            setImageResource(R.drawable.ic_color_picker_pipette)
            background = null
            // Center the glyph in the top-aligned frame, not in its extra touch-only height.
            val frameOffset = (resources.getDimensionPixelSize(R.dimen.color_picker_touch_target) - historyArtworkSize()) / 2
            setPadding(dp(8), dp(8) - frameOffset, dp(8), dp(8) + frameOffset)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { onPipetteRequested() }
        }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.color_picker_touch_target), Gravity.TOP))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun setColor(color: Int, preserveAlpha: Boolean = false) {
        // An explicit palette/history choice supersedes (never confirms) an unfinished draft.
        finishHexEditing()
        Color.colorToHSV(color, hsv)
        if (!preserveAlpha) alpha = Color.alpha(color)
        sliders[0].progress = hsv[0].toInt()
        sliders[1].progress = (hsv[1] * 100).toInt()
        sliders[2].progress = (hsv[2] * 100).toInt()
        if (!preserveAlpha && !lockAlpha && sliders.size > 3) sliders[3].progress = alpha
        update()
    }
    private fun addSlider(label: String, max: Int, value: Int, set: (Int) -> Unit) {
        addView(TextView(context).apply { text = label; setTextColor(Color.BLACK) })
        addView(SeekBar(context).apply {
            this.max = max
            progress = value
            progressTintList = ColorStateList.valueOf(Color.rgb(32, 201, 151))
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(130, 138, 145))
            thumbTintList = ColorStateList.valueOf(Color.rgb(52, 58, 64))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    // Synchronizing slider positions must not quantize a selected history color.
                    if (!fromUser) return
                    finishHexEditing()
                    set(progress)
                    update()
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }.also { sliders += it })
    }
    private fun update() {
        val color = Color.HSVToColor(alpha, hsv)
        preview.setBackgroundColor(color)
        preview.setTextColor(if (isDark(color)) Color.WHITE else Color.BLACK)
        confirmedHex = String.format("#%08X", color)
        // Slider synchronization and redraws must not replace a draft or move its cursor.
        if (!editingHex) preview.setText(confirmedHex)
        copyIcon.setColorFilter(preview.currentTextColor)
        copyIcon.contentDescription = context.getString(R.string.color_picker_copy_hex, confirmedHex)
        changed(color)
    }

    private fun isDark(color: Int): Boolean =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 128
}

/** Reflow real controls instead of shrinking their targets on narrow windows. */
private class SwatchRows(context: Context, private val swatches: List<View>, private val preferredColumns: Int) :
    LinearLayout(context) {
    private var columns = 0
    private val target = resources.getDimensionPixelSize(R.dimen.color_picker_touch_target)
    private val gap = resources.getDimensionPixelSize(R.dimen.color_picker_swatch_gap)

    init {
        orientation = VERTICAL
        arrange(preferredColumns)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val fittingColumns = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            preferredColumns else ((available + gap) / (target + gap)).coerceIn(1, preferredColumns)
        if (columns != fittingColumns) arrange(fittingColumns)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun arrange(count: Int) {
        columns = count
        swatches.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        removeAllViews()
        swatches.chunked(columns).forEachIndexed { rowIndex, row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEachIndexed { index, swatch ->
                    addView(swatch, LayoutParams(0, target, 1f).apply {
                        if (index > 0) marginStart = gap
                    })
                }
                // Keep the last row's cells the same width as the full rows.
                repeat(columns - row.size) {
                    addView(View(context), LayoutParams(0, target, 1f).apply { marginStart = gap })
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (rowIndex > 0) topMargin = gap
            })
        }
    }
}

/** RGB is opaque; eight digits are ARGB, including a leading zero alpha. */
internal fun parseHexColor(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    if (digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val value = digits.toLong(16)
    return (if (digits.length == 6) value or 0xFF000000L else value).toInt()
}
