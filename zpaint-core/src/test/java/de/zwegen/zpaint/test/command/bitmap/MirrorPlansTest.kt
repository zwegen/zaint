package de.zwegen.zpaint.test.command.bitmap

import de.zwegen.zpaint.command.bitmap.MirrorPlan
import de.zwegen.zpaint.command.bitmap.MirrorPlans
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorPlansTest {
    @Test
    fun `mirroring over the horizontal line translates by document height`() {
        assertEquals(MirrorPlan(1f, -1f, 0f, 25f), MirrorPlans.horizontalLine(25))
    }

    @Test
    fun `mirroring over the vertical line translates by document width`() {
        assertEquals(MirrorPlan(-1f, 1f, 40f, 0f), MirrorPlans.verticalLine(40))
    }
}
