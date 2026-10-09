package de.zwegen.zpaint.test.espresso.util

import android.content.ContextWrapper
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowInsets
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.util.wrappers.MainActivityImplementationExeption

data class MainActivityHelper(private val activity: ZaintEditorActivity) {

    val displaySize: Point
        get() {
            val displaySize = Point()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowManager = activity.windowManager
                val windowMetrics = windowManager.currentWindowMetrics
                val windowInsets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
                )
                val insetsWidth = (windowInsets.right + windowInsets.left).toFloat()
                val insetsHeight = (windowInsets.top + windowInsets.bottom).toFloat()
                val b = windowMetrics.bounds
                val width = b.width() - insetsWidth
                val height = b.height() - insetsHeight
                displaySize.x = width.toInt()
                displaySize.y = height.toInt()
            } else {
                activity.windowManager.defaultDisplay.getSize(displaySize)
            }
            return displaySize
        }
    val displayWidth: Int
        get() = displaySize.x
    val displayHeight: Int
        get() = displaySize.y
    var screenOrientation: Int
        get() = activity.requestedOrientation
        set(orientation) {
            activity.requestedOrientation = orientation
        }

    companion object {
        fun getMainActivityFromView(view: View): ZaintEditorActivity {
            var context = view.context
            while (context is ContextWrapper) {
                if (context is ZaintEditorActivity) {
                    return context
                }
                context = context.baseContext
            }
            throw MainActivityImplementationExeption("View context does not implement ZaintEditorActivity")
        }
    }
}
