package de.zwegen.zpaint.ui.tools

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.textfield.TextInputEditText
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.implementation.ShadowCastDirection

/** The top control always adjusts shadow blur, including for a cast shadow. */
class ZaintShadowOptionsPanel(private val topContainer: ViewGroup, optionsContainer: ViewGroup) {
    private val valueInput: TextInputEditText
    private val valueSeekBar: SeekBar
    private val offButton: ImageButton
    private val castDirectionButton: ImageButton
    private val innerDirectionButton: ImageButton
    private val groundButton: ImageButton
    private var callback: Callback? = null
    private var castDirection: ShadowCastDirection? = null
    private var innerDirection: ShadowCastDirection? = null
    private var groundShadow = false
    private var blur = MIN_BLUR
    private var normalSize = DEFAULT_NORMAL_SIZE
    private var castSize = DEFAULT_CAST_SIZE
    private var innerSize = DEFAULT_INNER_SIZE
    private val blurControl: ZaintTopNumberSlider

    init {
        val inflater = LayoutInflater.from(topContainer.context)
        val panel = inflater.inflate(R.layout.dialog_zpaint_shadow_tool, topContainer)
        val directionControls = inflater.inflate(R.layout.dialog_zpaint_shadow_cast_direction, optionsContainer)
        valueInput = panel.findViewById(R.id.zpaint_shadow_blur_input)
        valueSeekBar = panel.findViewById(R.id.zpaint_shadow_blur_seek_bar)
        offButton = directionControls.findViewById(R.id.zpaint_shadow_off)
        castDirectionButton = directionControls.findViewById(R.id.zpaint_shadow_cast_direction)
        innerDirectionButton = directionControls.findViewById(R.id.zpaint_shadow_inner_direction)
        groundButton = directionControls.findViewById(R.id.zpaint_shadow_ground)

        blurControl = ZaintTopNumberSlider(
            input = valueInput,
            seekBar = valueSeekBar,
            onValueChanged = { value, _ -> updateBlur(value, notify = true) }
        )
        offButton.setOnClickListener { selectOff(notify = true) }
        castDirectionButton.setOnClickListener {
            changeCastDirection(castDirection?.next() ?: ShadowCastDirection.DOWN_RIGHT, notify = true)
        }
        innerDirectionButton.setOnClickListener {
            changeInnerDirection(innerDirection?.next() ?: ShadowCastDirection.DOWN_RIGHT, notify = true)
        }
        groundButton.setOnClickListener { selectGround(notify = true) }
        configureBlurInput()
        updateBlur(blur, notify = false)
        selectOff(notify = false)
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    /** Stops object-shadow selection and explains why the active layer cannot produce one. */
    fun showShadowUnavailableInfo() {
        selectOff(notify = false)
        val content = LayoutInflater.from(topContainer.context).inflate(R.layout.dialog_zpaint_help, null)
        content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_title)
            .setText(R.string.zaint_shadow_unavailable_title)
        content.findViewById<AppCompatTextView>(R.id.zpaint_help_dialog_text)
            .setText(R.string.zaint_shadow_unavailable_message)
        val dialog = AlertDialog.Builder(topContainer.context, R.style.ZPaintAlertDialog)
            .setView(content)
            .create()
        content.findViewById<View>(R.id.zpaint_help_close_button).setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    fun showBlur(blur: Int) {
        this.blur = blur.coerceIn(MIN_BLUR, MAX_BLUR)
        updateBlur(this.blur, notify = false)
    }

    fun showCastDirection(direction: ShadowCastDirection?) = showCastDirection(direction, notify = false)

    /** Updates the cast-shadow controls from the handle shown directly on the canvas. */
    fun setCastFromHandle(direction: ShadowCastDirection, size: Int) {
        // The handle may now point between diagonal snap points. Keep the mode active and use
        // the closest diagonal solely as the button's visual indicator; changing the actual
        // direction here would overwrite its free angle in ShadowTool.
        castDirection = direction
        updateModeButtons()
        updateSize(size, notify = true)
    }

    fun setNormalFromHandle(size: Int) {
        selectOff(notify = true)
        updateSize(size, notify = true)
    }

    fun setInnerFromHandle(direction: ShadowCastDirection, size: Int) {
        innerDirection = direction
        updateModeButtons()
        updateSize(size, notify = true)
    }

    private fun showCastDirection(direction: ShadowCastDirection?, notify: Boolean) {
        changeCastDirection(direction, notify)
    }

    private fun changeCastDirection(direction: ShadowCastDirection?, notify: Boolean) {
        if (groundShadow) {
            groundShadow = false
            if (notify) callback?.onGroundShadowChanged(false)
        }
        if (direction != null && innerDirection != null) {
            innerDirection = null
            if (notify) callback?.onInnerDirectionChanged(null)
        }
        castDirection = direction
        updateModeButtons()
        if (notify) callback?.onCastDirectionChanged(direction)
        showSizeForCurrentMode(notify)
    }

    private fun changeInnerDirection(direction: ShadowCastDirection?, notify: Boolean) {
        if (groundShadow) {
            groundShadow = false
            if (notify) callback?.onGroundShadowChanged(false)
        }
        if (direction != null && castDirection != null) {
            castDirection = null
            if (notify) callback?.onCastDirectionChanged(null)
        }
        innerDirection = direction
        updateModeButtons()
        if (notify) callback?.onInnerDirectionChanged(direction)
        showSizeForCurrentMode(notify)
    }

    private fun selectOff(notify: Boolean) {
        val previousCastDirection = castDirection
        val previousInnerDirection = innerDirection
        castDirection = null
        innerDirection = null
        val previousGroundShadow = groundShadow
        groundShadow = false
        updateModeButtons()
        if (notify && previousCastDirection != null) callback?.onCastDirectionChanged(null)
        if (notify && previousInnerDirection != null) callback?.onInnerDirectionChanged(null)
        if (notify && previousGroundShadow) callback?.onGroundShadowChanged(false)
        showSizeForCurrentMode(notify)
    }

    private fun selectGround(notify: Boolean) {
        val previousCastDirection = castDirection
        val previousInnerDirection = innerDirection
        castDirection = null
        innerDirection = null
        groundShadow = true
        updateModeButtons()
        if (notify && previousCastDirection != null) callback?.onCastDirectionChanged(null)
        if (notify && previousInnerDirection != null) callback?.onInnerDirectionChanged(null)
        if (notify) callback?.onGroundShadowChanged(true)
    }

    private fun configureBlurInput() {
        valueInput.inputType = InputType.TYPE_CLASS_NUMBER
        valueInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(MIN_BLUR, MAX_BLUR))
        blurControl.setRange(MIN_BLUR, MAX_BLUR, blur)
    }

    private fun updateBlur(value: Int, notify: Boolean) {
        blur = value.coerceIn(MIN_BLUR, MAX_BLUR)
        blurControl.show(blur)
        if (notify) callback?.onBlurChanged(blur)
    }

    private fun updateSize(value: Int, notify: Boolean) {
        val size = value.coerceIn(MIN_SIZE, maxSizeForCurrentMode())
        when {
            castDirection != null -> castSize = size
            innerDirection != null -> innerSize = size
            else -> normalSize = size
        }
        if (notify) callback?.onSizeChanged(size)
    }

    private fun showSizeForCurrentMode(notify: Boolean) {
        val size = when {
            castDirection != null -> castSize
            innerDirection != null -> innerSize
            else -> normalSize
        }.coerceIn(MIN_SIZE, maxSizeForCurrentMode())
        if (innerDirection != null) innerSize = size
        if (notify) callback?.onSizeChanged(size)
    }

    private fun maxSizeForCurrentMode(): Int =
        if (innerDirection != null) MAX_INNER_SIZE else MAX_SIZE

    private fun updateModeButtons() {
        updateButton(offButton, R.drawable.ic_zpaint_shadow_normal, isActive = castDirection == null && innerDirection == null && !groundShadow)
        updateButton(castDirectionButton, R.drawable.ic_zpaint_shadow_cast_rotatable, isActive = castDirection != null, castDirection)
        updateButton(innerDirectionButton, R.drawable.ic_zpaint_shadow_inner_direction_rotatable, isActive = innerDirection != null, innerDirection)
        updateButton(groundButton, R.drawable.ic_zpaint_shadow_ground, isActive = groundShadow)
    }

    private fun updateButton(
        button: ImageButton,
        icon: Int,
        isActive: Boolean,
        direction: ShadowCastDirection? = null
    ) {
        button.isSelected = isActive
        button.backgroundTintList = null
        button.setImageResource(icon)
        direction?.let { button.drawable.level = it.iconLevel }
        button.clearColorFilter()
    }

    interface Callback {
        fun onBlurChanged(value: Int)
        fun onSizeChanged(value: Int)
        fun onCastDirectionChanged(direction: ShadowCastDirection?)
        fun onInnerDirectionChanged(direction: ShadowCastDirection?)
        fun onGroundShadowChanged(enabled: Boolean)
    }

    companion object {
        const val MIN_BLUR = 0
        const val MAX_BLUR = 100
        const val MIN_SIZE = 0
        const val MAX_SIZE = 100
        /** An inner shadow can at most reach the centre of the shape. */
        const val MAX_INNER_SIZE = 50
        const val DEFAULT_NORMAL_SIZE = 0
        const val DEFAULT_CAST_SIZE = 100
        const val DEFAULT_INNER_SIZE = 10
    }
}
