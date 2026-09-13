package de.zwegen.zpaint.ui.tools

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.widget.ImageButton
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.common.ANIMATION_DURATION
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.tools.options.ToolOptionsVisibilityController

class ZaintToolOptionsControllerHost(
    val activity: Activity,
    val idlingResource: CountingIdlingResource
) : ZaintToolOptionsController {
    private val bottomNavigation: ViewGroup =
        activity.findViewById(R.id.zpaint_main_bottom_navigation)
    private val mainToolOptions: ViewGroup =
        activity.findViewById(R.id.zpaint_layout_tool_options)
    private val topBarSpecificViewCheckmark: ImageButton =
        activity.findViewById(R.id.zpaint_btn_top_checkmark)
    private val topBarSpecificViewLayerPlus: View =
        activity.findViewById(R.id.zpaint_btn_top_layer_plus)
    private val topBar: View =
        activity.findViewById(R.id.zpaint_layout_top_bar)

    private var toolOptionsShown = false
    private var enabled = true
    private var hideButtonsEnabled = true
    private var callback: ToolOptionsVisibilityController.Callback? = null

    override val toolSpecificOptionsLayout: ViewGroup
        get() = activity.findViewById(R.id.zpaint_layout_tool_specific_options)

    override val toolSpecificOptionsTopLayout: ViewGroup
        get() = activity.findViewById(R.id.zpaint_layout_tool_specific_options_top)

    override val isVisible: Boolean
        get() = toolOptionsShown

    init {
        mainToolOptions.visibility = View.INVISIBLE
    }

    private fun notifyHide() {
        callback?.onHide()
    }

    private fun notifyShow() {
        callback?.onShow()
    }

    override fun resetToOrigin() {
        toolOptionsShown = false
        mainToolOptions.visibility = View.INVISIBLE
        mainToolOptions.y = bottomNavigation.y + bottomNavigation.height
    }

    override fun hide() {
        if (!enabled || !hideButtonsEnabled) {
            return
        }
        idlingResource.increment()
        toolOptionsShown = false
        mainToolOptions.animate().cancel()
        mainToolOptions.animate()
            .alpha(0f)
            .setDuration(CONTEXT_OPTIONS_FADE_DURATION)
            .withEndAction {
                if (!toolOptionsShown) {
                    mainToolOptions.visibility = View.GONE
                    mainToolOptions.alpha = 1f
                }
            }
            .start()
        notifyHide()
        idlingResource.decrement()
    }

    override fun hideImmediately() {
        if (!enabled || !hideButtonsEnabled) {
            return
        }
        toolOptionsShown = false
        mainToolOptions.animate().cancel()
        mainToolOptions.visibility = View.GONE
        mainToolOptions.alpha = 1f
        notifyHide()
    }

    override fun disable() {
        enabled = false
        if (isVisible) {
            resetToOrigin()
        }
    }

    override fun enable() {
        enabled = true
    }

    override fun show() {
        if (!enabled || !hideButtonsEnabled) {
            return
        }
        idlingResource.increment()
        toolOptionsShown = true
        mainToolOptions.animate().cancel()
        mainToolOptions.alpha = 0f
        mainToolOptions.visibility = View.VISIBLE
        mainToolOptions.post {
            val yPos = bottomNavigation.y - mainToolOptions.height
            mainToolOptions.y = yPos
            mainToolOptions.animate()
                .alpha(1f)
                .setDuration(CONTEXT_OPTIONS_FADE_DURATION)
                .start()
        }
        notifyShow()
        idlingResource.decrement()
    }

    override fun showDelayed() {
        toolSpecificOptionsLayout.post { show() }
    }

    override fun removeToolViews() {
        toolSpecificOptionsLayout.removeAllViews()
        toolSpecificOptionsTopLayout.removeAllViews()
        callback = null
    }

    override fun setCallback(callback: ToolOptionsVisibilityController.Callback) {
        this.callback = callback
    }

    override fun showCheckmark() {
        topBarSpecificViewCheckmark.imageTintList = ColorStateList.valueOf(Color.WHITE)
        topBarSpecificViewCheckmark.setImageResource(R.drawable.ic_zpaint_checkmark)
        topBarSpecificViewCheckmark.isEnabled = true
        topBarSpecificViewCheckmark.visibility = View.VISIBLE
    }

    override fun showLayerCheckmark() {
        topBarSpecificViewCheckmark.imageTintList = null
        topBarSpecificViewCheckmark.setImageResource(R.drawable.ic_zpaint_layer_insert_selector)
        topBarSpecificViewCheckmark.isEnabled = true
        topBarSpecificViewCheckmark.visibility = View.VISIBLE
    }

    override fun hideCheckmark() {
        topBarSpecificViewCheckmark.imageTintList = ColorStateList.valueOf(
            activity.getColor(R.color.zpaint_disabled_top_bar_icon)
        )
        topBarSpecificViewCheckmark.setImageResource(R.drawable.ic_zpaint_checkmark)
        topBarSpecificViewCheckmark.isEnabled = false
        topBarSpecificViewCheckmark.visibility = View.VISIBLE
    }

    override fun showLayerPlus() {
        topBarSpecificViewLayerPlus.visibility = View.VISIBLE
    }

    override fun hideLayerPlus() {
        topBarSpecificViewLayerPlus.visibility = View.GONE
    }

    override fun enableHide() {
        hideButtonsEnabled = true
    }

    override fun disableHide() {
        hideButtonsEnabled = false
    }

    override fun slideUp(view: View, willHide: Boolean, showOptionsView: Boolean, setViewGone: Boolean) {
        if (!enabled || !hideButtonsEnabled) {
            return
        }

        if (!willHide) {
            view.visibility = View.VISIBLE
            toolOptionsShown = showOptionsView
        }

        val animation: TranslateAnimation = if (willHide) {
            TranslateAnimation(
                0F,
                0F,
                0F,
                -view.height.toFloat()
            )
        } else {
            TranslateAnimation(
                0F,
                0F,
                view.height.toFloat(),
                0F
            )
        }

        animation.duration = ANIMATION_DURATION
        view.startAnimation(animation)
        if (willHide) {
            view.visibility = if (setViewGone) View.GONE else View.INVISIBLE
            toolOptionsShown = showOptionsView
            notifyHide()
        } else {
            notifyShow()
        }
    }

    override fun slideDown(view: View, willHide: Boolean, showOptionsView: Boolean, setViewGone: Boolean) {
        if (!enabled || !hideButtonsEnabled) {
            return
        }

        val animation: TranslateAnimation = if (willHide) {
            TranslateAnimation(
                0F,
                0F,
                0F,
                view.height.toFloat()
            )
        } else {
            TranslateAnimation(
                0F,
                0F,
                -view.height.toFloat(),
                0F
            )
        }

        animation.duration = ANIMATION_DURATION
        view.startAnimation(animation)
        if (willHide) {
            view.visibility = if (setViewGone) View.GONE else View.INVISIBLE
            toolOptionsShown = showOptionsView
            notifyHide()
        } else {
            view.visibility = View.VISIBLE
            toolOptionsShown = showOptionsView
            notifyShow()
        }
    }

    private companion object {
        const val CONTEXT_OPTIONS_FADE_DURATION = 180L
    }

}
