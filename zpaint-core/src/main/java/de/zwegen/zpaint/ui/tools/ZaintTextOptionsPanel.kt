package de.zwegen.zpaint.ui.tools

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Checkable
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.textfield.TextInputEditText
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.ZaintFontFamily
import de.zwegen.zpaint.tools.GoogleFontRepository
import de.zwegen.zpaint.tools.TextFont
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.ZaintTextOptions
import de.zwegen.zpaint.tools.options.ZaintTextEffect
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ZaintTextOptionsPanel(rootView: ViewGroup) : ZaintTextOptions {
    private val context: Context = rootView.context
    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ZaintTextOptions.Callback? = null
    private val fontList: RecyclerView
    private val underlinedToggleButton: MaterialButton
    private val italicToggleButton: MaterialButton
    private val boldToggleButton: MaterialButton
    private val textEffectToggleGroup: MaterialButtonToggleGroup
    private val shadowEffectButton: MaterialButton
    private val backgroundEffectButton: MaterialButton
    private var fontTypes: List<TextFont>
    private var selectedFont = TextFont.builtIn(ZaintFontFamily.INTER)
    private val topLayout: View
    private val bottomLayout: View
    private val lineSpacingSeekBar: SeekBar
    private val lineSpacingLabel: TextView
    private var isUpdatingLineSpacingSeekBar = false
    private var lineSpacingPercent = LINE_SPACING_DEFAULT_PERCENT
    private var letterSpacingPercent = LETTER_SPACING_DEFAULT_PERCENT
    private var text = ""
    private var textEffect = ZaintTextEffect.NONE
    private var isSyncingTextEffect = false
    private var textAlignment = Paint.Align.LEFT
    private var justified = false
    private var textInputDialog: AlertDialog? = null
    private var dialogTextEditText: EditText? = null

    init {
        val inflater = LayoutInflater.from(context)
        val textToolView = inflater.inflate(R.layout.dialog_zpaint_text_tool, rootView)
        topLayout = textToolView.findViewById(R.id.zpaint_text_top_layout)
        bottomLayout = textToolView.findViewById(R.id.zpaint_text_bottom_layout)
        fontList = textToolView.findViewById(R.id.zpaint_text_tool_dialog_list_font)
        underlinedToggleButton =
            textToolView.findViewById(R.id.zpaint_text_tool_dialog_toggle_underlined)
        italicToggleButton =
            textToolView.findViewById(R.id.zpaint_text_tool_dialog_toggle_italic)
        boldToggleButton = textToolView.findViewById(R.id.zpaint_text_tool_dialog_toggle_bold)
        textEffectToggleGroup =
            textToolView.findViewById(R.id.zpaint_text_tool_dialog_effect_group)
        shadowEffectButton =
            textToolView.findViewById(R.id.zpaint_text_tool_dialog_effect_shadow)
        backgroundEffectButton =
            textToolView.findViewById(R.id.zpaint_text_tool_dialog_effect_background)
        shadowEffectButton.icon = ZaintSoftShadowEffectIcon()
        backgroundEffectButton.icon = ZaintBackgroundEffectIcon()
        lineSpacingSeekBar = textToolView.findViewById(R.id.zpaint_text_tool_line_spacing_seekbar)
        lineSpacingLabel = textToolView.findViewById(R.id.zpaint_text_tool_line_spacing_label)
        fontTypes = loadFontTypes()
        initializeListeners()
    }

    private fun initializeListeners() {
        fontList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        fontList.adapter = ZaintFontPickerAdapter(context, fontTypes, { font ->
            notifyFontChanged(font)
            hideKeyboard()
        }, {
            showFontSearchDialog()
            hideKeyboard()
        }, { font ->
            showDeleteCustomFontDialog(font)
            hideKeyboard()
        })

        underlinedToggleButton.setOnClickListener { view ->
            callback?.setUnderlined((view as Checkable).isChecked)
            hideKeyboard()
        }
        italicToggleButton.setOnClickListener { view ->
            val italic = (view as Checkable).isChecked
            if (!selectedFont.supportsStyle(boldToggleButton.isChecked, italic)) {
                italicToggleButton.isChecked = false
            } else {
                callback?.setItalic(italic)
            }
            hideKeyboard()
        }
        boldToggleButton.setOnClickListener { view ->
            val bold = (view as Checkable).isChecked
            if (!selectedFont.supportsStyle(bold, italicToggleButton.isChecked)) {
                boldToggleButton.isChecked = false
            } else {
                callback?.setBold(bold)
            }
            hideKeyboard()
        }
        lineSpacingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingLineSpacingSeekBar) {
                    updateSpacingFromProgress(progress)
                    hideKeyboard()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                updateSpacingFromProgress(seekBar.progress)
            }
        })
        textEffectToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isSyncingTextEffect) {
                return@addOnButtonCheckedListener
            }
            textEffect = when {
                isChecked && checkedId == R.id.zpaint_text_tool_dialog_effect_outline ->
                    ZaintTextEffect.OUTLINE
                isChecked && checkedId == R.id.zpaint_text_tool_dialog_effect_shadow ->
                    ZaintTextEffect.SHADOW
                isChecked && checkedId == R.id.zpaint_text_tool_dialog_effect_background ->
                    ZaintTextEffect.BACKGROUND
                group.checkedButtonId == View.NO_ID -> ZaintTextEffect.NONE
                else -> return@addOnButtonCheckedListener
            }
            callback?.setTextEffect(textEffect)
            hideKeyboard()
        }
    }

    private fun notifyFontChanged(textFont: TextFont) {
        selectedFont = textFont
        callback?.setFont(textFont)
        setFontStyleAvailability(textFont.supportsBold, textFont.supportsItalic)
    }

    private fun notifyLineSpacingChanged(lineSpacingPercent: Int) {
        callback?.setLineSpacingPercent(lineSpacingPercent)
    }

    private fun notifyLetterSpacingChanged(letterSpacingPercent: Int) {
        callback?.setLetterSpacingPercent(letterSpacingPercent)
    }

    private fun notifyTextAlignmentChanged(textAlignment: Paint.Align, justified: Boolean) {
        callback?.setTextAlignment(textAlignment, justified)
    }

    private fun dialogAlignmentButtonId(textAlignment: Paint.Align, justified: Boolean): Int =
        if (justified) {
            R.id.zpaint_text_input_align_justify
        } else {
        when (textAlignment) {
            Paint.Align.CENTER -> R.id.zpaint_text_input_align_center
            Paint.Align.RIGHT -> R.id.zpaint_text_input_align_right
            else -> R.id.zpaint_text_input_align_left
        }
        }

    private fun notifyTextChanged(text: String) {
        this.text = text
        updateSpacingControl()
        callback?.setText(text)
    }

    override fun setState(
        bold: Boolean,
        italic: Boolean,
        underlined: Boolean,
        text: String,
        textSize: Int,
        textFont: TextFont,
        lineSpacingPercent: Int,
        letterSpacingPercent: Int,
        textAlignment: Paint.Align,
        justified: Boolean,
        outline: Boolean,
        shadow: Boolean,
        textBackground: Boolean
    ) {
        boldToggleButton.isChecked = bold
        italicToggleButton.isChecked = italic
        underlinedToggleButton.isChecked = underlined
        textEffect = when {
            outline -> ZaintTextEffect.OUTLINE
            shadow -> ZaintTextEffect.SHADOW
            textBackground -> ZaintTextEffect.BACKGROUND
            else -> ZaintTextEffect.NONE
        }
        isSyncingTextEffect = true
        when (textEffect) {
            ZaintTextEffect.OUTLINE ->
                textEffectToggleGroup.check(R.id.zpaint_text_tool_dialog_effect_outline)
            ZaintTextEffect.SHADOW ->
                textEffectToggleGroup.check(R.id.zpaint_text_tool_dialog_effect_shadow)
            ZaintTextEffect.BACKGROUND ->
                textEffectToggleGroup.check(R.id.zpaint_text_tool_dialog_effect_background)
            else -> textEffectToggleGroup.clearChecked()
        }
        isSyncingTextEffect = false
        this.textAlignment = textAlignment
        this.justified = justified
        this.text = text
        this.lineSpacingPercent = lineSpacingPercent
        this.letterSpacingPercent = letterSpacingPercent
        selectedFont = textFont
        refreshInstalledFonts(textFont)
        setFontStyleAvailability(textFont.supportsBold, textFont.supportsItalic)
        updateSpacingControl()
    }

    override fun setCallback(listener: ZaintTextOptions.Callback) {
        callback = listener
    }

    override fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val windowToken = dialogTextEditText?.windowToken ?: bottomLayout.windowToken
        imm.hideSoftInputFromWindow(windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    override fun showKeyboard() {
        val input = dialogTextEditText ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun showTextInputDialog(currentText: String, clearInitialText: Boolean) {
        if (textInputDialog?.isShowing == true) {
            dialogTextEditText?.requestFocus()
            dialogTextEditText?.setSelection(dialogTextEditText?.text?.length ?: 0)
            showKeyboard()
            return
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(8).toFloat()
            }
        }
        val initialInputText = if (clearInitialText) "" else currentText
        val input = ZaintJustifiedEditText(context).apply {
            setText(initialInputText)
            hint = context.getString(R.string.text_tool_dialog_input_hint)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
            textSize = 16f
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP or when (this@ZaintTextOptionsPanel.textAlignment) {
                Paint.Align.CENTER -> Gravity.CENTER_HORIZONTAL
                Paint.Align.RIGHT -> Gravity.END
                else -> Gravity.START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                justificationMode = android.graphics.text.LineBreaker.JUSTIFICATION_MODE_NONE
            }
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(52, 58, 64))
                cornerRadius = dp(6).toFloat()
            }
            backgroundTintList = null
            isJustifiedPreviewEnabled = this@ZaintTextOptionsPanel.justified
        }
        var lineWrapBaseText = initialInputText
        var isApplyingLineWrap = false
        var caseBaseText = initialInputText
        var isApplyingCase = false
        fun applyTextCase(transform: (String) -> String) {
            isApplyingCase = true
            input.setText(transform(caseBaseText))
            input.setSelection(input.text?.length ?: 0)
            isApplyingCase = false
        }
        val textCaseRow = LayoutInflater.from(context).inflate(
            R.layout.zpaint_text_case_buttons,
            container,
            false
        ) as LinearLayout
        val uppercaseCaseButton = textCaseRow.findViewById<MaterialButton>(R.id.zpaint_text_case_uppercase)
        val titleCaseButton = textCaseRow.findViewById<MaterialButton>(R.id.zpaint_text_case_title)
        val lowercaseCaseButton = textCaseRow.findViewById<MaterialButton>(R.id.zpaint_text_case_lowercase)
        val dialogAlignmentToggleGroup = textCaseRow.findViewById<MaterialButtonToggleGroup>(
            R.id.zpaint_text_input_alignment_group
        )
        val formatControlsRow = textCaseRow.findViewById<LinearLayout>(
            R.id.zpaint_text_input_format_controls_row
        )
        val textCaseButtons = listOf(
            uppercaseCaseButton,
            titleCaseButton,
            lowercaseCaseButton
        )
        val textCaseTransforms = mapOf<MaterialButton, (String) -> String>(
            uppercaseCaseButton to { value -> value.uppercase(Locale.getDefault()) },
            titleCaseButton to { value -> value.toTitleCase(Locale.getDefault()) },
            lowercaseCaseButton to { value -> value.lowercase(Locale.getDefault()) }
        )
        var selectedTextCaseButton: MaterialButton? = null
        fun selectTextCase(button: MaterialButton) {
            selectedTextCaseButton = if (selectedTextCaseButton === button) null else button
            textCaseButtons.forEach { it.isChecked = it === selectedTextCaseButton }
            val transform = selectedTextCaseButton?.let(textCaseTransforms::get) ?: { value: String -> value }
            applyTextCase(transform)
        }
        textCaseButtons.forEach { button ->
            button.isCheckable = true
            button.setOnClickListener { selectTextCase(button) }
        }
        fun applyDialogTextAlignment(alignment: Paint.Align, justified: Boolean) {
            textAlignment = alignment
            this@ZaintTextOptionsPanel.justified = justified
            input.gravity = Gravity.TOP or if (justified) {
                Gravity.START
            } else when (alignment) {
                Paint.Align.CENTER -> Gravity.CENTER_HORIZONTAL
                Paint.Align.RIGHT -> Gravity.END
                else -> Gravity.START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                input.justificationMode = android.graphics.text.LineBreaker.JUSTIFICATION_MODE_NONE
            }
            input.isJustifiedPreviewEnabled = justified
            notifyTextAlignmentChanged(alignment, justified)
        }
        dialogAlignmentToggleGroup.check(dialogAlignmentButtonId(textAlignment, justified))
        dialogAlignmentToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val alignment =
                    when (checkedId) {
                        R.id.zpaint_text_input_align_center -> Paint.Align.CENTER
                        R.id.zpaint_text_input_align_right -> Paint.Align.RIGHT
                        else -> Paint.Align.LEFT
                    }
                applyDialogTextAlignment(
                    alignment,
                    checkedId == R.id.zpaint_text_input_align_justify
                )
            }
        }
        val lineWrapRow = LayoutInflater.from(context).inflate(
            R.layout.zpaint_text_line_wrap_control,
            container,
            false
        )
        val lineWrapValue = lineWrapRow.findViewById<TextInputEditText>(R.id.zpaint_text_line_wrap_value)
        val lineWrapSeekBar = lineWrapRow.findViewById<SeekBar>(R.id.zpaint_text_line_wrap_seekbar)
        lineWrapValue.filters = arrayOf(DefaultNumberRangeFilter(LINE_WRAP_DEFAULT, LINE_WRAP_MAX))
        fun applyLineWrap(lineCount: Int) {
            val wrappedText = if (lineCount <= LINE_WRAP_DEFAULT) {
                lineWrapBaseText
            } else {
                balancedWrapText(lineWrapBaseText, lineCount, createLineWrapMeasurePaint(input))
            }
            isApplyingLineWrap = true
            input.setText(wrappedText)
            input.setSelection(input.text?.length ?: 0)
            isApplyingLineWrap = false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(editable: Editable) {
                input.invalidate()
                if (!isApplyingLineWrap) {
                    lineWrapBaseText = editable.toString()
                }
                if (!isApplyingCase) {
                    caseBaseText = editable.toString()
                }
            }
        })
        ZaintTopNumberSlider(
            input = lineWrapValue,
            seekBar = lineWrapSeekBar,
            onValueChanged = { lineCount, _ -> applyLineWrap(lineCount) }
        ).setRange(LINE_WRAP_DEFAULT, LINE_WRAP_MAX, LINE_WRAP_DEFAULT)
        val cancelButton = AppCompatButton(context).apply {
            text = context.getString(android.R.string.cancel)
            setTextColor(Color.WHITE)
            setBackgroundResource(de.zwegen.zpaint.colorpicker.R.drawable.color_picker_cancel_button_bg)
            backgroundTintList = null
            minWidth = dp(112)
            minHeight = dp(48)
            setPadding(dp(16), 0, dp(16), 0)
        }
        val okButton = AppCompatButton(context).apply {
            text = context.getString(android.R.string.ok)
            setTextColor(Color.WHITE)
            setBackgroundResource(de.zwegen.zpaint.colorpicker.R.drawable.color_picker_confirm_button_bg)
            backgroundTintList = null
            minWidth = dp(112)
            minHeight = dp(48)
            setPadding(dp(16), 0, dp(16), 0)
        }
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, dp(14), 0, 0)
            addView(cancelButton, LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                marginEnd = dp(8)
            })
            addView(okButton, LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ))
        }

        container.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        container.addView(textCaseRow)
        container.addView(lineWrapRow)
        container.addView(buttonRow)

        val dialog = AlertDialog.Builder(context, R.style.ZPaintAlertDialog)
            .setView(container)
            .create()

        fun closeDialog() {
            hideKeyboard()
            dialog.dismiss()
        }

        cancelButton.setOnClickListener { closeDialog() }
        okButton.setOnClickListener {
            this.text = input.text.toString()
            notifyTextChanged(this.text)
            closeDialog()
        }
        dialog.setOnDismissListener {
            dialogTextEditText = null
            textInputDialog = null
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            textCaseRow.post {
                if (formatControlsRow.width < dp(240)) {
                    formatControlsRow.removeView(dialogAlignmentToggleGroup)
                    textCaseRow.addView(
                        dialogAlignmentToggleGroup,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.END
                            topMargin = dp(4)
                        }
                    )
                }
            }
            dialogTextEditText = input
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            input.post { showKeyboard() }
        }
        textInputDialog = dialog
        dialog.show()
    }

    override fun getTopLayout(): View = topLayout

    override fun getBottomLayout(): View = bottomLayout
    override fun setShapeSizeText(shapeSize: String) = Unit

    override fun toggleShapeSizeVisibility(isVisible: Boolean) = Unit

    private fun setFontStyleAvailability(canBold: Boolean, canItalic: Boolean) {
        boldToggleButton.isEnabled = canBold
        italicToggleButton.isEnabled = canItalic
        if (!canBold) {
            boldToggleButton.isChecked = false
        }
        if (!canItalic) {
            italicToggleButton.isChecked = false
        }
    }

    private fun String.toTitleCase(locale: Locale): String {
        return lowercase(locale).replace(Regex("\\b\\p{L}")) { match ->
            match.value.uppercase(locale)
        }
    }

    private fun updateLineSpacingLabel(lineSpacingPercent: Int) {
        val displayValue = lineSpacingPercent
            .coerceIn(LINE_SPACING_MIN_PERCENT, LINE_SPACING_MAX_PERCENT) - LINE_SPACING_DEFAULT_PERCENT
        lineSpacingLabel.text = if (displayValue > 0) {
            "+$displayValue"
        } else {
            displayValue.toString()
        }
    }

    private fun updateLetterSpacingLabel(letterSpacingPercent: Int) {
        val displayValue = letterSpacingPercent
            .coerceIn(LETTER_SPACING_MIN_PERCENT, LETTER_SPACING_MAX_PERCENT)
        lineSpacingLabel.text = if (displayValue > 0) "+$displayValue" else displayValue.toString()
    }

    private fun setLineSpacingSeekBarPercent(lineSpacingPercent: Int) {
        isUpdatingLineSpacingSeekBar = true
        lineSpacingSeekBar.progress = lineSpacingPercent
            .coerceIn(LINE_SPACING_MIN_PERCENT, LINE_SPACING_MAX_PERCENT) - LINE_SPACING_MIN_PERCENT
        isUpdatingLineSpacingSeekBar = false
    }

    private fun setLetterSpacingSeekBarPercent(letterSpacingPercent: Int) {
        isUpdatingLineSpacingSeekBar = true
        lineSpacingSeekBar.progress = letterSpacingPercent
            .coerceIn(LETTER_SPACING_MIN_PERCENT, LETTER_SPACING_MAX_PERCENT) - LETTER_SPACING_MIN_PERCENT
        isUpdatingLineSpacingSeekBar = false
    }

    private fun progressToLineSpacingPercent(progress: Int): Int =
        (progress + LINE_SPACING_MIN_PERCENT)
            .coerceIn(LINE_SPACING_MIN_PERCENT, LINE_SPACING_MAX_PERCENT)

    private fun snapLineSpacingPercent(lineSpacingPercent: Int): Int {
        val displayValue = lineSpacingPercent - LINE_SPACING_DEFAULT_PERCENT
        return if (displayValue in -LINE_SPACING_ZERO_SNAP_DISTANCE..LINE_SPACING_ZERO_SNAP_DISTANCE) {
            LINE_SPACING_DEFAULT_PERCENT
        } else {
            lineSpacingPercent
        }
    }

    private fun progressToLetterSpacingPercent(progress: Int): Int =
        (progress + LETTER_SPACING_MIN_PERCENT)
            .coerceIn(LETTER_SPACING_MIN_PERCENT, LETTER_SPACING_MAX_PERCENT)

    private fun snapLetterSpacingPercent(letterSpacingPercent: Int): Int =
        if (letterSpacingPercent in -LETTER_SPACING_ZERO_SNAP_DISTANCE..LETTER_SPACING_ZERO_SNAP_DISTANCE) {
            LETTER_SPACING_DEFAULT_PERCENT
        } else {
            letterSpacingPercent
        }

    private fun updateSpacingFromProgress(progress: Int) {
        if (isSingleLineText()) {
            letterSpacingPercent = snapLetterSpacingPercent(progressToLetterSpacingPercent(progress))
            setLetterSpacingSeekBarPercent(letterSpacingPercent)
            updateLetterSpacingLabel(letterSpacingPercent)
            notifyLetterSpacingChanged(letterSpacingPercent)
        } else {
            lineSpacingPercent = snapLineSpacingPercent(progressToLineSpacingPercent(progress))
            setLineSpacingSeekBarPercent(lineSpacingPercent)
            updateLineSpacingLabel(lineSpacingPercent)
            notifyLineSpacingChanged(lineSpacingPercent)
        }
    }

    private fun updateSpacingControl() {
        if (isSingleLineText()) {
            setLetterSpacingSeekBarPercent(letterSpacingPercent)
            updateLetterSpacingLabel(letterSpacingPercent)
        } else {
            setLineSpacingSeekBarPercent(lineSpacingPercent)
            updateLineSpacingLabel(lineSpacingPercent)
        }
    }

    private fun isSingleLineText(): Boolean = !text.contains('\n')

    private fun createLineWrapMeasurePaint(input: EditText): Paint =
        Paint(input.paint).apply {
            typeface = selectedTypeface()
        }

    private fun selectedTypeface(): Typeface {
        val style = when {
            boldToggleButton.isChecked && italicToggleButton.isChecked -> Typeface.BOLD_ITALIC
            boldToggleButton.isChecked -> Typeface.BOLD
            italicToggleButton.isChecked -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val builtInFont = selectedFont.builtInFont
        return when {
            selectedFont.isCustom -> GoogleFontRepository.typefaceFor(context, selectedFont, style)
            builtInFont == ZaintFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, style)
            builtInFont == ZaintFontFamily.SERIF -> Typeface.create(Typeface.SERIF, style)
            builtInFont == ZaintFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, style)
            else -> builtInFont?.fontResourceFor(
                boldToggleButton.isChecked,
                italicToggleButton.isChecked
            )
                ?.let { runCatching { ResourcesCompat.getFont(context, it) }.getOrNull() }
                ?: Typeface.create(Typeface.SANS_SERIF, style)
        }
    }

    private fun balancedWrapText(rawText: String, targetLines: Int, paint: Paint): String {
        val normalizedText = rawText.trim().replace(Regex("\\s+"), " ")
        if (targetLines <= LINE_WRAP_DEFAULT || normalizedText.isEmpty()) {
            return normalizedText
        }
        val words = normalizedText.split(" ").filter { it.isNotEmpty() }
        val lineCount = targetLines.coerceIn(LINE_WRAP_DEFAULT, LINE_WRAP_MAX).coerceAtMost(words.size)
        if (lineCount <= LINE_WRAP_DEFAULT) {
            return normalizedText
        }

        val lineWidths = Array(words.size) { FloatArray(words.size) }
        for (start in words.indices) {
            val builder = StringBuilder()
            for (end in start until words.size) {
                if (end > start) {
                    builder.append(' ')
                }
                builder.append(words[end])
                lineWidths[start][end] = paint.measureText(builder.toString())
            }
        }

        val targetWidth = lineWidths[0][words.lastIndex] / lineCount
        val cost = Array(lineCount + 1) { DoubleArray(words.size + 1) { Double.POSITIVE_INFINITY } }
        val previousBreak = Array(lineCount + 1) { IntArray(words.size + 1) { -1 } }
        cost[0][0] = 0.0

        for (line in 1..lineCount) {
            for (end in line..words.size) {
                for (start in (line - 1) until end) {
                    val previousCost = cost[line - 1][start]
                    if (previousCost.isInfinite()) {
                        continue
                    }
                    val widthDifference = lineWidths[start][end - 1] - targetWidth
                    val candidateCost = previousCost + widthDifference * widthDifference
                    if (candidateCost < cost[line][end]) {
                        cost[line][end] = candidateCost.toDouble()
                        previousBreak[line][end] = start
                    }
                }
            }
        }

        val breaks = mutableListOf<Int>()
        var end = words.size
        for (line in lineCount downTo 1) {
            val start = previousBreak[line][end]
            if (start < 0) {
                return normalizedText
            }
            breaks.add(start)
            end = start
        }
        breaks.reverse()

        return breaks.mapIndexed { index, start ->
            val nextBreak = breaks.getOrNull(index + 1) ?: words.size
            words.subList(start, nextBreak).joinToString(" ")
        }.joinToString("\n")
    }

    private fun loadFontTypes(): List<TextFont> =
        ZaintFontFamily.pickerFamilies().map { TextFont.builtIn(it) } + GoogleFontRepository.installedFonts(context)

    private fun refreshInstalledFonts(selectedFont: TextFont = this.selectedFont) {
        fontTypes = loadFontTypes()
        (fontList.adapter as? ZaintFontPickerAdapter)?.replaceFonts(fontTypes, selectedFont)
    }

    private fun showDeleteCustomFontDialog(font: TextFont) {
        if (!font.isCustom) {
            return
        }
        val buttonBackground = ColorStateList.valueOf(Color.rgb(52, 58, 64))
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(8).toFloat()
            }
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.text_tool_dialog_font_delete_title)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
        }
        val message = TextView(context).apply {
            text = context.getString(R.string.text_tool_dialog_font_delete_message, font.displayName.orEmpty())
            textSize = 15f
            setTextColor(Color.BLACK)
            setPadding(0, dp(10), 0, dp(10))
        }
        val cancelButton = Button(context).apply {
            text = context.getString(android.R.string.cancel)
            setTextColor(Color.WHITE)
            backgroundTintList = buttonBackground
            minHeight = dp(44)
        }
        val deleteButton = Button(context).apply {
            text = context.getString(R.string.text_tool_dialog_font_delete_confirm)
            setTextColor(Color.WHITE)
            backgroundTintList = buttonBackground
            minHeight = dp(44)
        }
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            addView(cancelButton, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(8)
            })
            addView(deleteButton, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
        }
        container.addView(title)
        container.addView(message)
        container.addView(buttonRow)

        val dialog = AlertDialog.Builder(context, R.style.ZPaintAlertDialog)
            .setView(container)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        deleteButton.setOnClickListener {
                val deleted = GoogleFontRepository.deleteFont(context, font)
                if (deleted) {
                    val nextSelectedFont = if (selectedFont.id == font.id) {
                        TextFont.builtIn(ZaintFontFamily.INTER)
                    } else {
                        selectedFont
                    }
                    selectedFont = nextSelectedFont
                    refreshInstalledFonts(nextSelectedFont)
                    if (nextSelectedFont.id != font.id) {
                        notifyFontChanged(nextSelectedFont)
                    }
                    dialog.dismiss()
                } else {
                    // The dialog stays open if the font could not be deleted.
                }
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun showFontSearchDialog() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(8).toFloat()
            }
        }
        val searchInput = EditText(context).apply {
            hint = context.getString(R.string.text_tool_dialog_font_search_hint)
            setSingleLine(true)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
            textSize = 16f
            setPadding(dp(14), 0, dp(14), 0)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(52, 58, 64))
                cornerRadius = dp(6).toFloat()
            }
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_zpaint_search, 0, 0, 0)
            compoundDrawablePadding = dp(8)
        }
        val progressBar = ProgressBar(context).apply {
            visibility = View.GONE
        }
        val resultsList = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(360)
            )
        }
        val buttonBackground = ColorStateList.valueOf(Color.rgb(52, 58, 64))
        val cancelButton = Button(context).apply {
            text = context.getString(android.R.string.cancel)
            setTextColor(Color.WHITE)
            backgroundTintList = buttonBackground
            minHeight = dp(44)
        }
        val loadMoreButton = Button(context).apply {
            text = context.getString(R.string.text_tool_dialog_font_load_more)
            setTextColor(Color.WHITE)
            backgroundTintList = buttonBackground
            minHeight = dp(44)
        }
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, dp(10), 0, 0)
            addView(cancelButton, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(8)
            })
            addView(loadMoreButton, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
        }

        container.addView(searchInput)
        container.addView(progressBar)
        container.addView(resultsList)
        container.addView(buttonRow)

        val dialog = AlertDialog.Builder(context, R.style.ZPaintAlertDialog)
            .setView(container)
            .create()

        var currentQuery = ""
        var loadedCount = 0
        val adapter = GoogleFontSearchAdapter { family ->
            progressBar.visibility = View.VISIBLE
            Thread {
                val result = runCatching { GoogleFontRepository.downloadFont(context, family) }
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    result.onSuccess { textFont ->
                        selectedFont = textFont
                        refreshInstalledFonts(textFont)
                        notifyFontChanged(textFont)
                        dialog.dismiss()
                    }
                }
            }.start()
        }
        resultsList.adapter = adapter
        dialog.setOnDismissListener { adapter.dispose() }

        fun loadResults(reset: Boolean) {
            if (reset) {
                loadedCount = 0
                adapter.setFamilies(emptyList())
            }
            progressBar.visibility = View.VISIBLE
            val query = currentQuery
            val offset = loadedCount
            Thread {
                val result = runCatching { GoogleFontRepository.searchFonts(query, offset, FONT_SEARCH_PAGE_SIZE) }
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    result.onSuccess { families ->
                        if (reset) {
                            adapter.setFamilies(families)
                        } else {
                            adapter.addFamilies(families)
                        }
                        loadedCount += families.size
                        loadMoreButton.visibility = if (families.size == FONT_SEARCH_PAGE_SIZE) View.VISIBLE else View.GONE
                    }.onFailure {
                        adapter.setFamilies(emptyList())
                        loadMoreButton.visibility = View.GONE
                    }
                }
            }.start()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(editable: Editable) {
                currentQuery = editable.toString()
                loadResults(reset = true)
            }
        })
        loadMoreButton.setOnClickListener { loadResults(reset = false) }
        cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            loadResults(reset = true)
        }
        dialog.show()
    }

    private inner class GoogleFontSearchAdapter(
        private val onDownloadClicked: (String) -> Unit
    ) : RecyclerView.Adapter<GoogleFontSearchAdapter.ViewHolder>() {
        private val families = mutableListOf<String>()
        private val previewTypefaces = ConcurrentHashMap<String, Typeface>()
        private val loadingPreviews = ConcurrentHashMap.newKeySet<String>()
        private val previewExecutor = Executors.newFixedThreadPool(2)
        @Volatile private var disposed = false

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                minimumHeight = dp(54)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val title = TextView(context).apply {
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.BLACK)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(12)
                }
            }
            val preview = TextView(context).apply {
                text = context.getString(R.string.text_tool_dialog_font_preview)
                textSize = 24f
                setTextColor(Color.BLACK)
                includeFontPadding = false
                maxLines = 1
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_GRAVITY
                layoutParams = LinearLayout.LayoutParams(
                    dp(150),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(title)
            row.addView(preview)
            return ViewHolder(row, title, preview)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val family = families[position]
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            holder.title.text = family
            holder.preview.typeface = previewTypefaces[family] ?: Typeface.DEFAULT
            if (!previewTypefaces.containsKey(family) && loadingPreviews.add(family)) {
                previewExecutor.execute {
                    val previewTypeface = GoogleFontRepository.previewTypeface(context, family)
                    if (previewTypeface != null) {
                        previewTypefaces[family] = previewTypeface
                    }
                    mainHandler.post {
                        loadingPreviews.remove(family)
                        if (!disposed && holder.adapterPosition in families.indices && families[holder.adapterPosition] == family) {
                            holder.preview.typeface = previewTypeface ?: Typeface.DEFAULT
                            holder.preview.invalidate()
                        }
                    }
                }
            }
            holder.itemView.setOnClickListener { onDownloadClicked(family) }
        }

        override fun getItemCount(): Int = families.size

        @SuppressLint("NotifyDataSetChanged")
        fun setFamilies(newFamilies: List<String>) {
            families.clear()
            families.addAll(newFamilies)
            // Search results are replaced as one complete page.
            notifyDataSetChanged()
        }

        fun addFamilies(newFamilies: List<String>) {
            val startPosition = families.size
            families.addAll(newFamilies)
            notifyItemRangeInserted(startPosition, newFamilies.size)
        }

        fun dispose() {
            disposed = true
            previewExecutor.shutdownNow()
        }

        private inner class ViewHolder(
            itemView: View,
            val title: TextView,
            val preview: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        private const val LINE_SPACING_MIN_PERCENT = 60
        private const val LINE_SPACING_DEFAULT_PERCENT = 100
        private const val LINE_SPACING_MAX_PERCENT = 140
        private const val LINE_SPACING_ZERO_SNAP_DISTANCE = 2
        private const val LETTER_SPACING_MIN_PERCENT = -40
        private const val LETTER_SPACING_DEFAULT_PERCENT = 0
        private const val LETTER_SPACING_MAX_PERCENT = 40
        private const val LETTER_SPACING_ZERO_SNAP_DISTANCE = 2
        private const val LINE_WRAP_DEFAULT = 1
        private const val LINE_WRAP_MAX = 8
        private const val FONT_SEARCH_PAGE_SIZE = 20
    }
}
