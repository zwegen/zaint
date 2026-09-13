package de.zwegen.zpaint.iotasks

import de.zwegen.zpaint.ZaintDocumentStorage.FileType
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object MetadataStripper {
    private const val JPEG_START = 0xD8
    private const val JPEG_SCAN = 0xDA
    private const val JPEG_COMMENT = 0xFE
    private const val JPEG_APP_FIRST = 0xE0
    private const val JPEG_APP_LAST = 0xEF
    private const val JPEG_MARKER_PREFIX = 0xFF
    private const val JPEG_RESTART_FIRST = 0xD0
    private const val JPEG_RESTART_LAST = 0xD7
    private const val JPEG_END = 0xD9
    private const val JPEG_TEMP_SUFFIX = ".metadata-stripped"
    private const val PNG_TEMP_SUFFIX = ".metadata-stripped"
    private const val PNG_CHUNK_LENGTH_LIMIT = 50_000_000
    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private val PNG_CRITICAL_CHUNKS = setOf("IHDR", "PLTE", "IDAT", "IEND")

    fun stripMetadata(file: File, fileType: FileType) {
        when (fileType) {
            FileType.JPG -> stripJpeg(file)
            FileType.PNG -> stripPng(file)
            else -> Unit
        }
    }

    private fun stripJpeg(file: File) {
        val tempFile = File(file.parentFile, file.name + JPEG_TEMP_SUFFIX)
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(tempFile).use { output ->
                    stripJpeg(input, output)
                }
            }
            tempFile.copyTo(file, overwrite = true)
        } finally {
            tempFile.delete()
        }
    }

    private fun stripJpeg(input: InputStream, output: OutputStream) {
        val dataInput = DataInputStream(input)
        val dataOutput = DataOutputStream(output)
        if (dataInput.readUnsignedByte() != JPEG_MARKER_PREFIX ||
            dataInput.readUnsignedByte() != JPEG_START
        ) {
            throw EOFException("Not a JPEG file.")
        }
        dataOutput.writeByte(JPEG_MARKER_PREFIX)
        dataOutput.writeByte(JPEG_START)

        var marker = readMarker(dataInput)
        while (true) {
            when {
                marker == JPEG_SCAN -> {
                    writeMarker(dataOutput, marker)
                    copySegment(dataInput, dataOutput)
                    marker = copyScanData(dataInput, dataOutput)
                }
                marker == JPEG_END -> {
                    writeMarker(dataOutput, marker)
                    return
                }
                marker in JPEG_RESTART_FIRST..JPEG_RESTART_LAST -> {
                    writeMarker(dataOutput, marker)
                    marker = readMarker(dataInput)
                }
                marker == 0x01 -> {
                    writeMarker(dataOutput, marker)
                    marker = readMarker(dataInput)
                }
                marker in JPEG_APP_FIRST..JPEG_APP_LAST || marker == JPEG_COMMENT -> {
                    skipSegment(dataInput)
                    marker = readMarker(dataInput)
                }
                else -> {
                    writeMarker(dataOutput, marker)
                    copySegment(dataInput, dataOutput)
                    marker = readMarker(dataInput)
                }
            }
        }
    }

    private fun copyScanData(input: DataInputStream, output: DataOutputStream): Int {
        while (true) {
            val value = try {
                input.readUnsignedByte()
            } catch (e: EOFException) {
                return JPEG_END
            }
            if (value != JPEG_MARKER_PREFIX) {
                output.writeByte(value)
                continue
            }

            var marker = try {
                input.readUnsignedByte()
            } catch (e: EOFException) {
                return JPEG_END
            }
            while (marker == JPEG_MARKER_PREFIX) {
                marker = try {
                    input.readUnsignedByte()
                } catch (e: EOFException) {
                    return JPEG_END
                }
            }
            when {
                marker == 0x00 -> {
                    output.writeByte(JPEG_MARKER_PREFIX)
                    output.writeByte(marker)
                }
                marker in JPEG_RESTART_FIRST..JPEG_RESTART_LAST -> {
                    writeMarker(output, marker)
                }
                else -> return marker
            }
        }
    }

    private fun writeMarker(output: DataOutputStream, marker: Int) {
        output.writeByte(JPEG_MARKER_PREFIX)
        output.writeByte(marker)
    }

    private fun readMarker(input: DataInputStream): Int {
        var value = input.readUnsignedByte()
        while (value != JPEG_MARKER_PREFIX) {
            value = input.readUnsignedByte()
        }
        do {
            value = input.readUnsignedByte()
        } while (value == JPEG_MARKER_PREFIX)
        return value
    }

    private fun copySegment(input: DataInputStream, output: DataOutputStream) {
        val length = input.readUnsignedShort()
        output.writeShort(length)
        val data = ByteArray(length - 2)
        input.readFully(data)
        output.write(data)
    }

    private fun skipSegment(input: DataInputStream) {
        val length = input.readUnsignedShort()
        val data = ByteArray(length - 2)
        input.readFully(data)
    }

    private fun stripPng(file: File) {
        val tempFile = File(file.parentFile, file.name + PNG_TEMP_SUFFIX)
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(tempFile).use { output ->
                    stripPng(input, output)
                }
            }
            tempFile.copyTo(file, overwrite = true)
        } finally {
            tempFile.delete()
        }
    }

    private fun stripPng(input: InputStream, output: OutputStream) {
        val dataInput = DataInputStream(input)
        val dataOutput = DataOutputStream(output)
        val signature = ByteArray(PNG_SIGNATURE.size)
        dataInput.readFully(signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw EOFException("Not a PNG file.")
        }
        dataOutput.write(signature)

        while (true) {
            val length = dataInput.readInt()
            if (length < 0 || length > PNG_CHUNK_LENGTH_LIMIT) {
                throw EOFException("Invalid PNG chunk length.")
            }
            val typeBytes = ByteArray(4)
            dataInput.readFully(typeBytes)
            val data = ByteArray(length)
            dataInput.readFully(data)
            val crc = ByteArray(4)
            dataInput.readFully(crc)
            val type = String(typeBytes, Charsets.US_ASCII)
            if (type in PNG_CRITICAL_CHUNKS) {
                dataOutput.writeInt(length)
                dataOutput.write(typeBytes)
                dataOutput.write(data)
                dataOutput.write(crc)
            }
            if (type == "IEND") {
                return
            }
        }
    }
}
