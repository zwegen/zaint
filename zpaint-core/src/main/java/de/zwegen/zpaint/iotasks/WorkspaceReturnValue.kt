package de.zwegen.zpaint.iotasks

import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.model.CommandManagerModel

/** Restored document state passed from file loading to the editor. */
class WorkspaceReturnValue(
    val commandManagerModel: CommandManagerModel?,
    val colorHistory: ColorHistory?
) {
    val hasUndoHistory: Boolean
        get() = commandManagerModel != null
}
