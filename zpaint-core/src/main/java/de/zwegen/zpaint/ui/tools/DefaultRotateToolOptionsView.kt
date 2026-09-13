/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.ui.tools

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatSeekBar
import com.google.android.material.textfield.TextInputEditText
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.RotateToolOptionsView

class DefaultRotateToolOptionsView(rootView: ViewGroup) : RotateToolOptionsView {
    private val angleInput: TextInputEditText
    private val angleSeekBar: AppCompatSeekBar
    private var callback: RotateToolOptionsView.Callback? = null
    private val topSlider: ZaintTopNumberSlider

    init {
        val optionsView = LayoutInflater.from(rootView.context)
            .inflate(R.layout.dialog_zpaint_rotate_tool, rootView)
        angleInput = optionsView.findViewById(R.id.zpaint_rotate_angle_input)
        angleSeekBar = optionsView.findViewById(R.id.zpaint_rotate_seek_bar)
        angleInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(ANGLE_MIN, ANGLE_MAX))
        topSlider = ZaintTopNumberSlider(
            input = angleInput,
            seekBar = angleSeekBar,
            onValueChanged = { value, source ->
                if (source == ZaintTopNumberSlider.Source.INPUT) callback?.onStartTracking()
                if (source == ZaintTopNumberSlider.Source.SLIDER) callback?.onAngleChanged(value)
                if (source == ZaintTopNumberSlider.Source.INPUT) callback?.onStopTracking(value)
            },
            onStartTracking = { callback?.onStartTracking() },
            onStopTracking = { value -> callback?.onStopTracking(value) },
        )
        topSlider.setRange(ANGLE_MIN, ANGLE_MAX, DEFAULT_ANGLE)

        optionsView.findViewById<View>(R.id.zpaint_rotate_left_btn)
            .setOnClickListener { callback?.rotateCounterClockwiseClicked() }
        optionsView.findViewById<View>(R.id.zpaint_rotate_right_btn)
            .setOnClickListener { callback?.rotateClockwiseClicked() }
        optionsView.findViewById<View>(R.id.zpaint_rotate_flip_horizontal_btn)
            .setOnClickListener { callback?.flipHorizontalClicked() }
        optionsView.findViewById<View>(R.id.zpaint_rotate_flip_vertical_btn)
            .setOnClickListener { callback?.flipVerticalClicked() }

    }

    override fun setCallback(callback: RotateToolOptionsView.Callback) {
        this.callback = callback
    }

    override fun setAngle(angle: Int) {
        topSlider.show(angle)
    }

    companion object {
        private const val ANGLE_MIN = -90
        private const val ANGLE_MAX = 90
        private const val DEFAULT_ANGLE = 0
    }
}
