package de.zwegen.zpaint.tools.implementation

import de.zwegen.zpaint.tools.Tool

/** Defines which framework state changes may discard an editable bubble. */
internal object SpeechBubbleStatePolicy {
    fun preservesPreview(stateChange: Tool.StateChange): Boolean =
        stateChange == Tool.StateChange.MOVE_CANCELED
}
