package de.zwegen.zpaint.test.command.replay

import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintAddLayerCommand
import de.zwegen.zpaint.command.implementation.ZaintPointCommand
import de.zwegen.zpaint.command.replay.SnapshotReplayCommandPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotReplayCommandPolicyTest {
    @Test
    fun acceptsOnlyExplicitlyWhitelistedCommandClasses() {
        assertTrue(SnapshotReplayCommandPolicy.supports(ZaintPointCommand::class.java))
        assertTrue(SnapshotReplayCommandPolicy.supports(ZaintAddLayerCommand::class.java))
        assertFalse(SnapshotReplayCommandPolicy.supports(Command::class.java))
    }
}
