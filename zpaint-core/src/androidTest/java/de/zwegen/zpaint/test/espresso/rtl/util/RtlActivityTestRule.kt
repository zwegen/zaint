package de.zwegen.zpaint.test.espresso.rtl.util

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.test.espresso.util.LanguageSupport
import java.util.*

class RtlActivityTestRule<T : Activity?>(activityClass: Class<T>?, private val language: String) : ActivityTestRule<T>(activityClass) {
    override fun beforeActivityLaunched() {
        super.beforeActivityLaunched()
        val locale = Locale(language)
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        LanguageSupport.setLocale(targetContext, locale)
    }

    override fun afterActivityFinished() {
        super.afterActivityFinished()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        LanguageSupport.setLocale(targetContext, Locale("en"))
    }
}
