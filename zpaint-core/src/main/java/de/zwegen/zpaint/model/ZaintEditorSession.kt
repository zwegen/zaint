package de.zwegen.zpaint.model

import android.net.Uri
import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.contract.ZaintEditorContracts

/**
 * Mutable session state owned by the Zaint editor screen.
 *
 * The state intentionally stays separate from the Activity so that saving,
 * restoring, and UI recreation use the same source of truth.
 */
open class ZaintEditorSession : ZaintEditorContracts.Model {
    private var wasInitialAnimationPlayed = false
    override var isSaved = false
    override var savedPictureUri: Uri? = null
    override var cameraImageUri: Uri? = null
    override var colorHistory: ColorHistory = ColorHistory()

    override fun wasInitialAnimationPlayed(): Boolean = wasInitialAnimationPlayed

    override fun setInitialAnimationPlayed(wasInitialAnimationPlayed: Boolean) {
        this.wasInitialAnimationPlayed = wasInitialAnimationPlayed
    }
}
