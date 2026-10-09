package de.zwegen.zpaint.test.command.snapshot

import android.graphics.Paint
import android.graphics.PointF
import de.zwegen.zpaint.command.snapshot.CommandInputSnapshot
import org.junit.Assert.assertNotSame
import org.junit.Test

class CommandInputSnapshotTest {
    private val snapshots = CommandInputSnapshot()

    @Test
    fun `copies paint independently`() {
        val source = Paint().apply { color = 0xFF224466.toInt() }

        val copy = snapshots.paint(source)

        assertNotSame(source, copy)
    }

    @Test
    fun `copies point lists independently`() {
        val source = mutableListOf(PointF(4f, 8f))

        val copy = snapshots.points(source)
        source[0].x = 12f

        assertNotSame(source, copy)
        assertNotSame(source[0], copy[0])
    }
}
