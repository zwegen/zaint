/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatSeekBar
import com.google.android.material.textfield.TextInputEditText
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.BorderToolOptionsView

class DefaultBorderToolOptionsView(rootView: ViewGroup) : BorderToolOptionsView {
    private val amountInput: TextInputEditText
    private val amountSeekBar: AppCompatSeekBar
    private var callback: BorderToolOptionsView.Callback? = null
    private val topSlider: ZaintTopNumberSlider

    init {
        val optionsView = LayoutInflater.from(rootView.context)
            .inflate(R.layout.dialog_zpaint_border_tool, rootView)
        amountInput = optionsView.findViewById(R.id.zpaint_border_amount_input)
        amountSeekBar = optionsView.findViewById(R.id.zpaint_border_seek_bar)
        amountInput.filters = arrayOf(DefaultNumberRangeFilter(BORDER_MIN, BORDER_MAX))
        topSlider = ZaintTopNumberSlider(
            input = amountInput,
            seekBar = amountSeekBar,
            onValueChanged = { value, source ->
                if (source == ZaintTopNumberSlider.Source.SLIDER) {
                    callback?.onBorderAmountChanged(value)
                } else {
                    callback?.onStartTracking()
                    callback?.onStopTracking(value)
                }
            },
            onStartTracking = { callback?.onStartTracking() },
            onStopTracking = { value -> callback?.onStopTracking(value) },
        )
        topSlider.setRange(BORDER_MIN, BORDER_MAX, DEFAULT_AMOUNT)
    }

    override fun setCallback(callback: BorderToolOptionsView.Callback) {
        this.callback = callback
    }

    override fun setBorderAmount(amount: Int) {
        topSlider.show(amount)
    }

    companion object {
        private const val BORDER_MIN = -50
        private const val BORDER_MAX = 50
        private const val DEFAULT_AMOUNT = 0
    }
}
