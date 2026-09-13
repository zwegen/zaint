package de.zwegen.zpaint.ui

import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View

private const val KEYBOARD_MIN_HEIGHT_DP = 300f

/** Tracks whether the root view is visibly reduced by the software keyboard. */
class KeyboardListener(private val rootView: View) {
    var isSoftKeyboardVisible = false

    init {
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            isSoftKeyboardVisible = keyboardTakesVisibleSpace()
        }
    }

    private fun keyboardTakesVisibleSpace(): Boolean {
        val coveredHeight = rootView.rootView.height - rootView.height
        return coveredHeight > dpToPx(rootView.resources.displayMetrics)
    }

    private fun dpToPx(displayMetrics: DisplayMetrics): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            KEYBOARD_MIN_HEIGHT_DP,
            displayMetrics
        )
}
