package de.zwegen.zpaint.command.serialization

import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.model.CommandManagerModel

/** Complete restorable state stored in a Zaint project or recovery file. */
data class ZaintProjectContent(
    val commandModel: CommandManagerModel,
    val colorHistory: ColorHistory?
)
