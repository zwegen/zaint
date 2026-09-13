package de.zwegen.zpaint.iotasks

import java.io.InputStream

internal object SvgContentDetector {
    private const val MAX_HEADER_BYTES = 8 * 1024
    private val SVG_TAG = Regex("""<svg(?=\s|>)""", RegexOption.IGNORE_CASE)

    fun isSvg(inputStream: InputStream): Boolean {
        val header = ByteArray(MAX_HEADER_BYTES)
        var length = 0
        while (length < header.size) {
            val bytesRead = inputStream.read(header, length, header.size - length)
            if (bytesRead <= 0) {
                break
            }
            length += bytesRead
        }
        return isSvg(header, length)
    }

    fun isSvg(bytes: ByteArray, length: Int = bytes.size): Boolean {
        if (length <= 0) {
            return false
        }
        val text = when {
            length >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, length - 2, Charsets.UTF_16BE)
            length >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, length - 2, Charsets.UTF_16LE)
            else -> String(bytes, 0, length, Charsets.UTF_8)
        }
        return SVG_TAG.containsMatchIn(text)
    }
}
