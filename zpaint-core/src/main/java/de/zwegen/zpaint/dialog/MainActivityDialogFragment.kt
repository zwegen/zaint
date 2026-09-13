package de.zwegen.zpaint.dialog

import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView

/**
 * Base dialog that obtains the Zaint screen presenter after it is attached to the
 * editor activity.
 */
open class MainActivityDialogFragment : AppCompatDialogFragment() {
    lateinit var presenter: ZaintEditorContracts.Presenter

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        presenter = hostView().presenter
    }

    private fun hostView(): MainView = requireActivity() as MainView
}
