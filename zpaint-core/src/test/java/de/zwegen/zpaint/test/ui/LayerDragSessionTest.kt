package de.zwegen.zpaint.test.ui

import de.zwegen.zpaint.ui.dragndrop.LayerDragSession
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerDragSessionTest {
    @Test
    fun finishesAsReorderAfterPreviewMoves() {
        val session = LayerDragSession(3)
        session.movePreviewTo(1)

        assertEquals(
            LayerDragSession.Outcome.Reorder(3, 1),
            session.finish()
        )
    }

    @Test
    fun finishesAsMergeWhenTargetIsSelected() {
        val session = LayerDragSession(2)
        session.selectMergeTarget(0)

        assertEquals(
            LayerDragSession.Outcome.Merge(2, 0),
            session.finish()
        )
    }
}
