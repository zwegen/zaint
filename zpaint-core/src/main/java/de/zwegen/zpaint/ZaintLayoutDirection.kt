package de.zwegen.zpaint

import android.text.TextUtils
import android.view.View
import java.util.Locale

/**
 * Decides whether Zaint must mirror layout-specific UI details for a locale.
 *
 * The app keeps its own drawables and layer presentation; Android supplies
 * only the locale direction used for choosing between their LTR and RTL forms.
 */
object ZaintLayoutDirection {
    fun usesRightToLeftLayout(locale: Locale = Locale.getDefault()): Boolean {
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    }
}
