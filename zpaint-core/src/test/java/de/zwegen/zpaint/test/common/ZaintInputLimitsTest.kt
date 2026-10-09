package de.zwegen.zpaint.test.common

import de.zwegen.zpaint.common.readBytesWithLimit
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ZaintInputLimitsTest {
    @Test
    fun readsInputAtTheConfiguredLimit() {
        val bytes = byteArrayOf(1, 2, 3)

        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBytesWithLimit(bytes.size))
    }

    @Test(expected = IOException::class)
    fun rejectsInputAboveTheConfiguredLimit() {
        ByteArrayInputStream(byteArrayOf(1, 2, 3)).readBytesWithLimit(2)
    }
}
