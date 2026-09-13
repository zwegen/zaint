package de.zwegen.zpaint.ui.tools

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.material.textfield.TextInputEditText
import de.zwegen.zpaint.R
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter

/** The direct size control for the deformation brush. */
class ZaintWarpOptionsPanel(container: ViewGroup) {
    private val sizeInput: TextInputEditText
    private val sizeSeekBar: SeekBar
    private var callback: Callback? = null
    private val sizeControl: ZaintTopNumberSlider

    init {
        val panel = LayoutInflater.from(container.context).inflate(R.layout.dialog_zpaint_warp_tool, container)
        sizeInput = panel.findViewById(R.id.zpaint_warp_size_input)
        sizeSeekBar = panel.findViewById(R.id.zpaint_warp_size_seek_bar)
        sizeInput.filters = arrayOf<InputFilter>(DefaultNumberRangeFilter(MIN_RADIUS, MAX_RADIUS))
        sizeControl = ZaintTopNumberSlider(
            input = sizeInput,
            seekBar = sizeSeekBar,
            onValueChanged = { value, _ -> callback?.onRadiusChanged(value) }
        )
        sizeControl.setRange(MIN_RADIUS, MAX_RADIUS, DEFAULT_RADIUS)
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun showRadius(radius: Int) = sizeControl.show(radius)

    interface Callback {
        fun onRadiusChanged(radius: Int)
    }

    companion object {
        const val MIN_RADIUS = 10
        const val MAX_RADIUS = 100
        const val DEFAULT_RADIUS = 50
    }
}
