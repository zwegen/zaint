package de.zwegen.zpaint.common

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

const val MAX_IMPORTED_IMAGE_PIXELS = 20_000_000L
const val MAX_ZAINT_PROJECT_BYTES = 100 * 1024 * 1024
const val MAX_ZAINT_PROJECT_PIXELS = 30_000_000L
const val MAX_SVG_BYTES = 5 * 1024 * 1024
const val MAX_FONT_BYTES = 10 * 1024 * 1024
const val MAX_FONT_METADATA_BYTES = 5 * 1024 * 1024

/** Reads untrusted input without allowing it to grow an in-memory buffer indefinitely. */
@Throws(IOException::class)
fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) return output.toByteArray()
        if (read > maxBytes - total) throw IOException("Input exceeds $maxBytes bytes")
        output.write(buffer, 0, read)
        total += read
    }
}

/** Stops parsers from consuming more than the allowed amount of an untrusted file. */
class ByteLimitInputStream(input: InputStream, private val maxBytes: Int) : FilterInputStream(input) {
    private var bytesRead = 0

    override fun read(): Int {
        if (bytesRead >= maxBytes) return rejectAdditionalInput()
        val value = super.read()
        if (value != -1) bytesRead++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRead >= maxBytes) return rejectAdditionalInput()
        val read = super.read(buffer, offset, minOf(length, maxBytes - bytesRead))
        if (read > 0) bytesRead += read
        return read
    }

    private fun rejectAdditionalInput(): Int {
        if (super.read() == -1) return -1
        throw IOException("Input exceeds $maxBytes bytes")
    }
}
