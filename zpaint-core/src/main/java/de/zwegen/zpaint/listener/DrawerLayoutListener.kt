package de.zwegen.zpaint.listener

import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.presenter.ZaintLayerPresenter

/** Keeps editing state current while the layer drawer changes visibility. */
class DrawerLayoutListener(
    private val activity: ZaintEditorActivity,
    private val layerPresenter: ZaintLayerPresenter
) : DrawerLayout.DrawerListener {
    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
        activity.hideKeyboard()
    }

    override fun onDrawerClosed(drawerView: View) {
        layerPresenter.invalidate()
    }

    override fun onDrawerOpened(drawerView: View) = Unit

    override fun onDrawerStateChanged(newState: Int) = Unit
}
