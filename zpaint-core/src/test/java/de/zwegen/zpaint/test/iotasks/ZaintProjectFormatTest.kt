package de.zwegen.zpaint.iotasks

import de.zwegen.zpaint.ZaintDocumentStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZaintProjectFormatTest {
    @Test
    fun recognizesNewAndLegacyZaintProjectExtensions() {
        assertTrue(ZaintProjectFormat.isZaintFileName("draft.znt"))
        assertTrue(ZaintProjectFormat.isZaintFileName("DRAFT.ZNT"))
        assertTrue(ZaintProjectFormat.isZaintFileName("draft.zaint"))
        assertTrue(ZaintProjectFormat.isZaintFileName("DRAFT.ZAINT"))
        assertFalse(ZaintProjectFormat.isZaintFileName("draft.zip"))
        assertFalse(ZaintProjectFormat.isZaintFileName("draft.png"))
    }

    @Test
    fun exposesItsOwnProjectMimeType() {
        assertTrue(ZaintProjectFormat.MIME_TYPE.contains("znt"))
    }

    @Test
    fun savesNewProjectsWithTheZntExtension() {
        assertEquals(".znt", ZaintDocumentStorage.FileType.ZAINT.toExtension())
    }
}
