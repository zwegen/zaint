package de.zwegen.zpaint

import android.content.SharedPreferences

/**
 * Zaint's small, explicit set of locally remembered editing settings.
 *
 * The image number is retained across app restarts.
 */
open class ZaintSettings(private val store: SharedPreferences) {
    open var nextImageNumber: Int
        get() = store.getInt(KEY_IMAGE_NUMBER, 0)
        set(value) = store.edit().putInt(KEY_IMAGE_NUMBER, value).apply()

    companion object {
        private const val KEY_IMAGE_NUMBER = "imagenumbertag"
    }
}
