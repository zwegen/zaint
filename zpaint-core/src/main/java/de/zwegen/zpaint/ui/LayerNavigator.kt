package de.zwegen.zpaint.ui

import android.content.Context
import de.zwegen.zpaint.contract.ZaintLayerContracts

/** Sends layer-operation feedback through Zaint's shared notification channel. */
class LayerNavigator(private val appContext: Context) : ZaintLayerContracts.Navigator {
    override fun showToast(id: Int, length: Int) {
        // Toasts are intentionally disabled throughout the editor.
    }
}
