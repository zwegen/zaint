package de.zwegen.zpaint.test.espresso.util

import android.content.Context
import android.os.Build
import java.util.*

class LanguageSupport private constructor() {
    companion object {
        fun setLocale(context: Context, locale: Locale?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Locale.setDefault(Locale.Category.DISPLAY, locale)
            } else {
                Locale.setDefault(locale)
            }
            val resources = context.resources
            val conf = resources.configuration
            conf.setLocale(locale)
            resources.updateConfiguration(conf, resources.displayMetrics)
        }
    }
}
