package de.zwegen.zpaint.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

@SuppressLint("ShowToast")
object ToastFactory {
    private var activeToast: Toast? = null

    private fun replaceToast(nextToast: Toast): Toast {
        activeToast?.cancel()
        activeToast = nextToast
        return nextToast
    }

    @JvmStatic
    fun makeText(context: Context, @StringRes resId: Int, duration: Int): Toast {
        return replaceToast(Toast.makeText(context, resId, duration))
    }

    @JvmStatic
    fun makeText(context: Context, msg: String, duration: Int): Toast {
        return replaceToast(Toast.makeText(context, msg, duration))
    }
}
