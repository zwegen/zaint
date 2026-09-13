package de.zwegen.zpaint.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.tools.ToolPaint

/** Android boundary required by [ZaintEditorNavigator]. */
interface ZaintEditorNavigationHost : ColorChangeTarget {
    val activity: AppCompatActivity
    val model: ZaintEditorContracts.Model
    val commandManager: ZaintCommandTimeline
    val presenter: ZaintEditorContracts.Presenter
    val idlingResource: CountingIdlingResource
    val toolPaint: ToolPaint

    /** Starts the normal canvas pipette and returns its color to the active tool. */
    fun selectPipetteColor()
}
