package de.zwegen.zpaint.test.command.bitmap

import de.zwegen.zpaint.command.bitmap.DocumentSize
import de.zwegen.zpaint.command.bitmap.QuarterTurnPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class QuarterTurnPlanTest {
    @Test
    fun `a quarter turn swaps document dimensions`() {
        assertEquals(DocumentSize(12, 10), QuarterTurnPlan(90f).rotatedDocumentSize(10, 12))
    }
}
