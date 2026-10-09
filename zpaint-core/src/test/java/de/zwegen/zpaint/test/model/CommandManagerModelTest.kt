package de.zwegen.zpaint.test.model

import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.model.CommandManagerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class CommandManagerModelTest {
    @Test
    fun snapshotKeepsInitialCommandAndCopiesSerializedCommands() {
        val initial = mock(Command::class.java)
        val restored = mock(Command::class.java)
        val source = mutableListOf(restored)

        val snapshot = CommandManagerModel(initial, source)
        source.clear()

        assertSame(initial, snapshot.initialCommand)
        assertEquals(listOf(restored), snapshot.commands)
    }
}
