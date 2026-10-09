package de.zwegen.zpaint.test.tools.implementation

import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.implementation.SpeechBubbleStatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBubbleStatePolicyTest {
    @Test
    fun pinchCancellationKeepsTheEditableBubble() {
        assertTrue(SpeechBubbleStatePolicy.preservesPreview(Tool.StateChange.MOVE_CANCELED))
    }

    @Test
    fun documentAndToolResetsDoNotKeepTheEditableBubble() {
        assertFalse(SpeechBubbleStatePolicy.preservesPreview(Tool.StateChange.NEW_IMAGE_LOADED))
        assertFalse(SpeechBubbleStatePolicy.preservesPreview(Tool.StateChange.RESET_INTERNAL_STATE))
    }
}
