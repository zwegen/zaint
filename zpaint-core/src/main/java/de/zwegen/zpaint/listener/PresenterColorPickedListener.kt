package de.zwegen.zpaint.listener

import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.contract.ZaintEditorContracts.Presenter

/** Routes a confirmed picker color to the current Zaint screen presenter. */
class PresenterColorPickedListener(private val presenter: Presenter) : OnColorPickedListener {
    override fun colorChanged(color: Int) {
        presenter.setBottomNavigationColor(color)
    }
}
