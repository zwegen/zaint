package de.zwegen.zpaint.tools.implementation

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import de.zwegen.zpaint.R
import de.zwegen.zpaint.colorpicker.R as ColorPickerR
import de.zwegen.zpaint.tools.ContextCallback

/** Android-backed services for Zaint drawing tools. */
class ZaintToolContextAdapter(override val context: Context) : ContextCallback {
    override val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    override val scrollTolerance: Int
        get() = (displayMetrics.widthPixels * SCROLL_TOLERANCE_RATIO).toInt()
    override val orientation: ContextCallback.ScreenOrientation
        get() = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ContextCallback.ScreenOrientation.LANDSCAPE
        } else {
            ContextCallback.ScreenOrientation.PORTRAIT
        }
    override val checkeredBitmapShader: Shader? by lazy {
        BitmapFactory.decodeResource(context.resources, ColorPickerR.drawable.zpaint_checkeredbg)?.let { bitmap ->
            BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    override fun showNotification(@StringRes messageId: Int) {
        showNotificationWithDuration(messageId, ContextCallback.NotificationDuration.SHORT)
    }

    override fun showNotificationWithDuration(
        @StringRes messageId: Int,
        duration: ContextCallback.NotificationDuration
    ) {
        // Toasts are intentionally disabled throughout the editor.
    }

    override fun getFont(@FontRes fontId: Int): Typeface? = ResourcesCompat.getFont(context, fontId)

    @ColorInt
    override fun getColor(@ColorRes colorId: Int): Int = ContextCompat.getColor(context, colorId)

    override fun getDrawable(@DrawableRes drawableId: Int): Drawable? =
        AppCompatResources.getDrawable(context, drawableId)

    private companion object {
        const val SCROLL_TOLERANCE_RATIO = 0.1f
    }
}
