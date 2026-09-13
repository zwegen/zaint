package de.zwegen.zpaint.tools.implementation

import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolReference

/** Stores the active Zaint tool; creation and disposal stay with the controller. */
class ZaintActiveToolReference(initialTool: Tool? = null) : ToolReference {
    override var tool: Tool? = initialTool
}
