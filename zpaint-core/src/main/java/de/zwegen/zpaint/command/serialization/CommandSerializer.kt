package de.zwegen.zpaint.command.serialization

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.common.ByteLimitInputStream
import de.zwegen.zpaint.common.MAX_IMPORTED_IMAGE_PIXELS
import de.zwegen.zpaint.common.MAX_ZAINT_PROJECT_BYTES
import de.zwegen.zpaint.common.MAX_ZAINT_PROJECT_PIXELS
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.implementation.ZaintDocumentDimensions
import de.zwegen.zpaint.command.implementation.ZaintLayerStackDocumentLoad
import de.zwegen.zpaint.command.implementation.ZaintSelectLayerCommand
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.iotasks.WorkspaceReturnValue
import de.zwegen.zpaint.iotasks.ZaintProjectFormat
import de.zwegen.zpaint.model.CommandManagerModel
import de.zwegen.zpaint.model.ZaintLayer
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Stores a Zaint project as a self-contained layer snapshot.
 *
 * The format deliberately persists image state rather than a list of drawing commands. A loaded
 * project therefore starts with a fresh undo history, while its layers, selected layer, visibility,
 * opacity and color history are restored exactly. This keeps project files independent from tool
 * implementation details and from command-class registration order.
 */
open class ZaintProjectSerializer(
    private val activityContext: Context,
    private val commandManager: ZaintCommandTimeline,
    private val model: ZaintEditorContracts.Model
) {
    companion object {
        private const val FORMAT_MAGIC = "ZAINT"
        private const val FORMAT_VERSION = 1
        private const val MAX_LAYER_COUNT = 100
        private const val MAX_ENCODED_BITMAP_BYTES = 32 * 1024 * 1024
        private const val MAX_COLOR_HISTORY_ENTRIES = 4
    }

    fun writeToFile(fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, ZaintProjectFormat.MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            activityContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.also { uri ->
                activityContext.contentResolver.openOutputStream(uri)?.use(::writeToStream)
                    ?: throw IOException("Could not open project output stream")
            }
        } else {
            val downloads = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists() && !downloads.mkdirs()) return null
            val file = File(downloads, fileName)
            FileOutputStream(file).use(::writeToStream)
            Uri.fromFile(file)
        }
    }

    fun overWriteFile(fileName: String, uri: Uri, resolver: ContentResolver): Uri? {
        return try {
            resolver.openOutputStream(uri, "wt")?.use(::writeToStream)
                ?.let { uri }
                ?: writeToFile(fileName)
        } catch (_: IOException) {
            writeToFile(fileName)
        }
    }

    fun writeToInternalMemory(stream: FileOutputStream) {
        stream.use(::writeToStream)
    }

    fun readFromInternalMemory(stream: FileInputStream): WorkspaceReturnValue {
        val project = stream.use(::readProject)
        return WorkspaceReturnValue(project.commandModel, project.colorHistory)
    }

    fun readFromFile(uri: Uri): ZaintProjectContent {
        val input = activityContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open project input stream")
        return input.use(::readProject)
    }

    private fun writeToStream(stream: OutputStream) {
        val snapshot = commandManager.captureProjectSnapshot()
            ?: throw IOException("There is no image state to save")
        try {
            DataOutputStream(stream).use { output ->
                output.writeUTF(FORMAT_MAGIC)
                output.writeInt(FORMAT_VERSION)
                writeDocumentState(output, snapshot)
                writeColorHistory(output)
            }
        } finally {
            snapshot.release()
        }
    }

    private fun writeDocumentState(
        output: DataOutputStream,
        snapshot: de.zwegen.zpaint.model.LayerModelSnapshot
    ) {
        output.writeInt(snapshot.width)
        output.writeInt(snapshot.height)
        output.writeInt(snapshot.layerCount)
        repeat(snapshot.layerCount) { index ->
            val metadata = snapshot.layers[index]
            output.writeBoolean(metadata.isVisible)
            output.writeInt(metadata.opacityPercentage)
            val bitmap = snapshot.copyBitmapAt(index)
            try {
                val encoded = bitmap.encodePng()
                output.writeInt(encoded.size)
                output.write(encoded)
            } finally {
                bitmap.recycle()
            }
        }
        output.writeInt(snapshot.currentLayerIndex ?: -1)
    }

    private fun writeColorHistory(output: DataOutputStream) {
        val colors = model.colorHistory.colors.takeLast(MAX_COLOR_HISTORY_ENTRIES)
        output.writeInt(colors.size)
        colors.forEach(output::writeInt)
    }

    private fun readProject(stream: InputStream): ZaintProjectContent {
        DataInputStream(ByteLimitInputStream(stream, MAX_ZAINT_PROJECT_BYTES)).use { input ->
            val magic = input.readUTF()
            if (magic != FORMAT_MAGIC) throw NotZaintProjectException("Not a Zaint project file")
            val version = input.readInt()
            if (version != FORMAT_VERSION) {
                throw NotZaintProjectException("Unsupported Zaint project version: $version")
            }
            val document = readDocumentState(input)
            val colorHistory = readColorHistory(input)
            if (input.read() != -1) throw NotZaintProjectException("Project contains trailing data")
            return ZaintProjectContent(
                commandModel = restoreModel(document.width, document.height, document.layers, document.selectedLayer),
                colorHistory = colorHistory
            )
        }
    }

    private fun readDocumentState(input: DataInputStream): DocumentState {
        val width = input.readInt().requirePositive("canvas width")
        val height = input.readInt().requirePositive("canvas height")
        val canvasPixels = width.toLong() * height.toLong()
        if (canvasPixels > MAX_IMPORTED_IMAGE_PIXELS) {
            throw NotZaintProjectException("Canvas is too large")
        }
        val layerCount = input.readInt()
        if (layerCount !in 1..MAX_LAYER_COUNT) {
            throw NotZaintProjectException("Invalid layer count: $layerCount")
        }
        if (canvasPixels * layerCount > MAX_ZAINT_PROJECT_PIXELS) {
            throw NotZaintProjectException("Project contains too many pixels")
        }
        val layers = ArrayList<ZaintLayer>(layerCount)
        repeat(layerCount) {
            val visible = input.readBoolean()
            val opacity = input.readInt()
            if (opacity !in 0..100) throw NotZaintProjectException("Invalid layer opacity")
            val size = input.readInt()
            if (size !in 1..MAX_ENCODED_BITMAP_BYTES) {
                throw NotZaintProjectException("Invalid bitmap size")
            }
            val encoded = ByteArray(size)
            input.readFully(encoded)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
            if (bounds.outWidth != width || bounds.outHeight != height) {
                throw NotZaintProjectException("Layer dimensions do not match the canvas")
            }
            val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
                ?: throw NotZaintProjectException("Invalid layer bitmap")
            val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
            if (bitmap !== decoded && !decoded.isRecycled) decoded.recycle()
            layers += ZaintLayer(bitmap).apply {
                isVisible = visible
                opacityPercentage = opacity
            }
        }
        val selectedLayer = input.readInt()
        if (selectedLayer !in -1 until layerCount) {
            layers.forEach { layer -> if (!layer.bitmap.isRecycled) layer.bitmap.recycle() }
            throw NotZaintProjectException("Invalid selected layer")
        }
        return DocumentState(width, height, layers, selectedLayer)
    }

    private fun restoreModel(
        width: Int,
        height: Int,
        layers: List<ZaintLayer>,
        selectedLayer: Int
    ): CommandManagerModel {
        val initialState = ZaintCommandBatch().apply {
            addCommand(ZaintDocumentDimensions(width, height))
            addCommand(ZaintLayerStackDocumentLoad(layers))
            if (selectedLayer >= 0) addCommand(ZaintSelectLayerCommand(selectedLayer))
        }
        return CommandManagerModel(initialState, mutableListOf())
    }

    private data class DocumentState(
        val width: Int,
        val height: Int,
        val layers: List<ZaintLayer>,
        val selectedLayer: Int
    )

    private fun readColorHistory(input: DataInputStream): ColorHistory {
        val count = input.readInt()
        if (count !in 0..MAX_COLOR_HISTORY_ENTRIES) {
            throw NotZaintProjectException("Invalid color history")
        }
        return ColorHistory().apply {
            repeat(count) { addColor(input.readInt()) }
        }
    }

    private fun Bitmap.encodePng(): ByteArray {
        val output = ByteArrayOutputStream()
        check(compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode layer bitmap" }
        return output.toByteArray()
    }

    private fun Int.requirePositive(name: String): Int {
        if (this <= 0) throw NotZaintProjectException("Invalid $name")
        return this
    }

    class NotZaintProjectException(message: String) : IOException(message)
}
