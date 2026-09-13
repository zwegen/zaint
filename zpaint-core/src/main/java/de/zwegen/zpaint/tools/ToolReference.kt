package de.zwegen.zpaint.tools

/** A shared pointer to the tool that is currently active in the Zaint editor. */
interface ToolReference {
    var tool: Tool?
}
