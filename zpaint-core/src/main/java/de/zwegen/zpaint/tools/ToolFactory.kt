package de.zwegen.zpaint.tools

import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController

/** Builds a configured interactive tool for the selected Zaint tool type. */
interface ToolFactory {
    fun createTool(
        toolType: ZaintToolKind,
        toolOptionsViewController: ZaintToolOptionsController,
        commandManager: ZaintCommandTimeline,
        workspace: Workspace,
        idlingResource: CountingIdlingResource,
        toolPaint: ToolPaint,
        contextCallback: ContextCallback,
        onColorPickedListener: OnColorPickedListener
    ): Tool
}
