package de.zwegen.zpaint.tools

import android.content.Context
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.annotation.StringRes

/** The Android services a drawing tool may use without knowing the activity. */
interface ContextCallback {
    val context: Context
    val displayMetrics: DisplayMetrics
    val checkeredBitmapShader: Shader?
    val scrollTolerance: Int
    val orientation: ScreenOrientation?

    fun showNotification(@StringRes messageId: Int)
    fun showNotificationWithDuration(@StringRes messageId: Int, duration: NotificationDuration)
    fun getFont(@FontRes fontId: Int): Typeface?

    @ColorInt
    fun getColor(@ColorRes colorId: Int): Int

    fun getDrawable(@DrawableRes drawableId: Int): Drawable?

    enum class ScreenOrientation { PORTRAIT, LANDSCAPE }
    enum class NotificationDuration { SHORT, LONG }
}
