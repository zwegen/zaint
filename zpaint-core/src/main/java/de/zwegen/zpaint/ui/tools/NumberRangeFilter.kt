package de.zwegen.zpaint.ui.tools

import android.text.InputFilter
import android.text.Spanned

/**
 * InputFilter contract for editable numeric tool settings.
 *
 * Implementations expose a mutable upper limit because transform dimensions can
 * change while their option panel stays open.
 */
interface NumberRangeFilter : InputFilter {
    var max: Int

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence?
}
