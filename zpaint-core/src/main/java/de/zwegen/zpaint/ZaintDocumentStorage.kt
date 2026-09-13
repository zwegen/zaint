package de.zwegen.zpaint

import android.app.Activity
import android.app.ActivityManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.destination
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import kotlinx.coroutines.runBlocking
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.common.ZAINT_RECOVERY_ENDING
import de.zwegen.zpaint.common.ZaintStorageDirectories.downloads
import de.zwegen.zpaint.common.ZaintStorageDirectories.pictures
import de.zwegen.zpaint.common.MAX_LAYERS
import de.zwegen.zpaint.common.MAX_IMPORTED_IMAGE_PIXELS
import de.zwegen.zpaint.common.TEMP_IMAGE_DIRECTORY_NAME
import de.zwegen.zpaint.common.TEMP_IMAGE_NAME
import de.zwegen.zpaint.common.TEMP_IMAGE_PATH
import de.zwegen.zpaint.common.TEMP_IMAGE_TEMP_PATH
import de.zwegen.zpaint.common.TEMP_PICTURE_NAME
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.iotasks.BitmapReturnValue
import de.zwegen.zpaint.iotasks.BitmapSize
import de.zwegen.zpaint.iotasks.DisplayBitmapScaler
import de.zwegen.zpaint.iotasks.MetadataStripper
import de.zwegen.zpaint.iotasks.WorkspaceReturnValue
import de.zwegen.zpaint.iotasks.ZaintProjectFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.sqrt

private const val CONSTANT_POINT9 = .9f
private const val CONSTANT_5000 = 5000L
private const val CONSTANT_4 = 4L
private const val CONSTANT_100 = 100
private const val ANGLE_90 = 90f
private const val ANGLE_180 = 180f
private const val ANGLE_270 = 270f
private const val BUFFER_SIZE = 4096

object ZaintDocumentStorage {
    @JvmField
    var filename = "image"

    @JvmField
    var fileType = FileType.PNG

    var compressQuality = CONSTANT_100

    @JvmField
    var compressFormat = CompressFormat.PNG

    var removeMetadata = true

    var navigator: ZaintEditorContracts.Navigator? = null

    @JvmField
    var storeImageUri: Uri? = null

    var sourceFolderName: String? = null

    /** Relative path of an opened image's original folder, e.g. "Pictures/Artwork". */
    var sourceRelativePath: String? = null

    var temporaryFilePath: String? = null

    val defaultFileName: String
        get() = filename + fileType.toExtension()

    private val cacheChildFolder = "images"

    enum class FileType(val value: String) {
        PNG("png"),
        JPG("jpg"),
        ZAINT("znt");

        fun toExtension(): String = ".$value"
    }

    val saveRelativePath: String
        get() = sourceRelativePath ?: Environment.DIRECTORY_PICTURES

    val imageMimeType: String
        get() = when (fileType) {
            FileType.JPG -> "image/jpeg"
            else -> "image/png"
        }

    val currentMimeType: String
        get() = when (fileType) {
            FileType.ZAINT -> ZaintProjectFormat.MIME_TYPE
            else -> imageMimeType
        }

    @Throws(IOException::class)
    private fun saveBitmapToStream(outputStream: OutputStream?, bitmap: Bitmap?) {
        var currentBitmap = bitmap
        require(currentBitmap != null && !currentBitmap.isRecycled) { "Bitmap is invalid" }
        if (compressFormat == CompressFormat.JPEG) {
            val newBitmap =
                Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, currentBitmap.config)
            val canvas = Canvas(newBitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(currentBitmap, 0f, 0f, null)
            currentBitmap = newBitmap
        }
        if (outputStream == null || !currentBitmap.compress(
                compressFormat,
                compressQuality,
                outputStream
            )
        ) {
            throw IOException("Can not write png to stream.")
        }
    }

    @Throws(IOException::class)
    fun saveBitmapToUri(uri: Uri, bitmap: Bitmap?, context: Context): Uri {
        val cachedFile = createCompressedBitmapFile(bitmap, context, UUID.randomUUID().toString())
        try {
            if (!copyFileToUri(cachedFile, context.contentResolver, uri)) {
                throw IOException("Can not open URI.")
            }
        } finally {
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
        }
        return uri
    }

    fun compress(context: Context, fileToCompress: File?, destination: Uri): Boolean = runBlocking {
        compressWithCurrentSettings(context, fileToCompress, destination)
    }

    private suspend fun compressWithCurrentSettings(context: Context, fileToCompress: File?, destination: Uri): Boolean {
        fileToCompress ?: return false
        val tempFileName = TEMP_PICTURE_NAME
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var compressed: File? = null
            try {
                val cachePath = File(context.cacheDir, cacheChildFolder)
                cachePath.mkdirs()
                compressed = Compressor.compress(context, fileToCompress) {
                    quality(compressQuality)
                    format(compressFormat)
                    destination(File(cachePath, tempFileName + fileType.toExtension()))
                }
                removeMetadataIfNeeded(compressed)
                context.contentResolver.openOutputStream(destination, "rwt")?.use { os ->
                    FileInputStream(compressed).use { input ->
                        copyStreams(input, os)
                    }
                }
                true
            } catch (e: IOException) {
                Log.e("Compression", "Can not compress image file.", e)
                false
            } finally {
                if (compressed != null && compressed.exists()) {
                    compressed.delete()
                }
            }
        } else {
            try {
                val destinationFile = destination.path?.let(::File) ?: return false
                val compressed = Compressor.compress(context, fileToCompress) {
                    quality(compressQuality)
                    format(compressFormat)
                    destination(destinationFile)
                }
                removeMetadataIfNeeded(compressed)
                true
            } catch (e: IOException) {
                Log.e("Compression", "Can not compress image file", e)
                false
            }
        }
    }

    fun saveBitmapToFile(
        fileName: String,
        bitmap: Bitmap?,
        resolver: ContentResolver?,
        context: Context
    ): Uri {
        val imageUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, imageMimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, saveRelativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            resolver?.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val targetDirectory = File(Environment.getExternalStorageDirectory(), saveRelativePath)
            if (!(targetDirectory.exists() || targetDirectory.mkdirs())) {
                throw IOException("Can not create media directory.")
            }
            Uri.fromFile(File(targetDirectory, fileName))
        }

        val cachedFile = createCompressedBitmapFile(bitmap, context, UUID.randomUUID().toString())
        try {
            if (imageUri == null || resolver == null || !copyFileToUri(cachedFile, resolver, imageUri)) {
                throw IOException("Can not write image file.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishedValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver?.update(imageUri, publishedValues, null, null)
                resolver?.notifyChange(imageUri, null)
            }
            return imageUri
        } catch (e: IOException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && imageUri != null) {
                resolver?.delete(imageUri, null, null)
            }
            throw e
        } finally {
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
        }
    }

    fun saveBitmapToCache(bitmap: Bitmap?, context: Context, fileName: String): Uri? {
        var uri: Uri? = null
        try {
            val newFile = createCompressedBitmapFile(bitmap, context, fileName)
            val fileProviderString =
                context.applicationContext.packageName + ".fileprovider"
            uri = FileProvider.getUriForFile(
                context.applicationContext,
                fileProviderString,
                newFile
            )
        } catch (e: IOException) {
            Log.e("Can not write", "Can not write png to stream.", e)
        }
        return uri
    }

    @Throws(IOException::class)
    private fun createCompressedBitmapFile(bitmap: Bitmap?, context: Context, fileName: String): File {
        val cachePath = File(context.cacheDir, cacheChildFolder)
        cachePath.mkdirs()
        val imageFile = File(cachePath, fileName + fileType.toExtension())
        FileOutputStream(imageFile).use { stream ->
            saveBitmapToStream(stream, bitmap)
        }
        removeMetadataIfNeeded(imageFile)
        return imageFile
    }

    private fun copyFileToUri(file: File, resolver: ContentResolver, uri: Uri): Boolean = try {
        resolver.openOutputStream(uri, "rwt")?.use { output ->
            FileInputStream(file).use { input ->
                copyStreams(input, output)
            }
        } != null
    } catch (e: IOException) {
        Log.e("ZaintDocumentStorage", "Can not copy file to URI.", e)
        false
    }

    private fun removeMetadataIfNeeded(file: File?) {
        if (!removeMetadata || file == null || !file.exists()) {
            return
        }
        try {
            MetadataStripper.stripMetadata(file, fileType)
        } catch (e: Exception) {
            Log.e("Metadata", "Can not remove image metadata.", e)
        }
    }

    @Throws(NullPointerException::class)
    @JvmStatic
    fun createNewEmptyPictureFile(filename: String?, activity: Activity?): File {
        var fileName = filename ?: defaultFileName
        if (!fileName.lowercase(Locale.US).endsWith(fileType.toExtension())) {
            fileName += fileType.toExtension()
        }
        val externalFilesDir = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (externalFilesDir == null || !externalFilesDir.exists() && !externalFilesDir.mkdir()) {
            throw NullPointerException("Can not create media directory.")
        }
        return File(externalFilesDir, fileName)
    }

    @Throws(IOException::class)
    fun decodeBitmapFromUri(
        resolver: ContentResolver,
        uri: Uri,
        options: BitmapFactory.Options
    ): Bitmap? {
        val inputStream =
            resolver.openInputStream(uri) ?: throw IOException("Can't open input stream")
        return inputStream.use {
            val bitmap = BitmapFactory.decodeStream(it, null, options)
            if (options.inJustDecodeBounds) {
                return bitmap
            }
            val angle = getBitmapOrientationFromInputStream(resolver, uri)
            getOrientedBitmap(bitmap, angle)
        }
    }

    @Throws(IOException::class)
    private fun getBitmapOrientationFromInputStream(
        resolver: ContentResolver,
        uri: Uri
    ): Float {
        val inputStream = resolver.openInputStream(uri) ?: return 0f
        return inputStream.use {
            val exifInterface = ExifInterface(it)
            getBitmapOrientation(exifInterface)
        }
    }

    @JvmStatic
    fun getOrientedBitmap(bitmap: Bitmap?, angle: Float): Bitmap? {
        bitmap ?: return null
        val matrix = Matrix()
        matrix.postRotate(angle)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotatedBitmap
    }

    @JvmStatic
    fun getBitmapOrientation(exifInterface: ExifInterface?): Float {
        exifInterface ?: return 0f
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        var angle = 0f
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> angle = ANGLE_90
            ExifInterface.ORIENTATION_ROTATE_180 -> angle = ANGLE_180
            ExifInterface.ORIENTATION_ROTATE_270 -> angle = ANGLE_270
        }
        return angle
    }

    fun parseFileName(uri: Uri, resolver: ContentResolver) {
        var fileName = "image"
        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.Images.ImageColumns.DISPLAY_NAME),
            null, null, null
        )
        cursor?.use {
            if (cursor.moveToFirst()) {
                fileName =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DISPLAY_NAME))
            }
        }
        val lowerFileName = fileName.lowercase(Locale.US)
        if (lowerFileName.endsWith(FileType.JPG.toExtension())) {
            fileType = FileType.JPG
            compressFormat = CompressFormat.JPEG
            filename = fileName.substring(0, fileName.length - fileType.toExtension().length)
        } else if (lowerFileName.endsWith(".jpeg")) {
            fileType = FileType.JPG
            compressFormat = CompressFormat.JPEG
            filename = fileName.substring(0, fileName.length - ".jpeg".length)
        } else if (lowerFileName.endsWith(FileType.PNG.toExtension())) {
            fileType = FileType.PNG
            compressFormat = CompressFormat.PNG
            filename = fileName.substring(0, fileName.length - fileType.toExtension().length)
        }
    }

    @JvmStatic
    fun saveFileFromUri(uri: Uri, destFile: File, context: Context) {
        try {
            context.contentResolver.openInputStream(uri)?.use { fileInputStream ->
                FileOutputStream(destFile).use { fileOutputStream ->
                    copyStreams(fileInputStream, fileOutputStream)
                }
            }
        } catch (e: IOException) {
            Log.e("ZaintDocumentStorage", "Can not copy streams.", e)
        }
    }

    @Throws(IOException::class)
    private fun copyStreams(from: InputStream, to: OutputStream): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total: Long = 0
        while (true) {
            val read = from.read(buffer)
            if (read == -1) {
                break
            }
            to.write(buffer, 0, read)
            total += read.toLong()
        }
        return total
    }

    private fun getUriForFilename(
        contentLocationUri: Uri,
        filename: String,
        resolver: ContentResolver,
        relativePath: String? = null
    ): Uri? {
        val selectionArgs = arrayOf(filename)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val normalizedRelativePath = relativePath?.trim('/')
        val cursor = resolver.query(contentLocationUri, null, selection, selectionArgs, null)
        cursor?.run {
            while (moveToNext()) {
                val fileName = getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val relativePathMatches =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && normalizedRelativePath != null) {
                        val relativePathColumn = getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        relativePathColumn == -1 ||
                            getString(relativePathColumn)?.trim('/') == normalizedRelativePath
                    } else {
                        true
                    }
                if (fileName == filename && relativePathMatches) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    close()
                    return ContentUris.withAppendedId(contentLocationUri, id)
                }
            }
            close()
        }
        return null
    }

    fun getUriForFilenameInPicturesFolder(filename: String, resolver: ContentResolver): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            getUriForFilename(contentUri, filename, resolver, Environment.DIRECTORY_PICTURES)
        } else {
            val file = File(pictures, filename)
            return if (file.exists()) {
                Uri.fromFile(file)
            } else {
                null
            }
        }
    }

    fun getUriForFilenameInDownloadsFolder(filename: String, resolver: ContentResolver): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            getUriForFilename(contentUri, filename, resolver, Environment.DIRECTORY_DOWNLOADS)
        } else {
            val file = File(downloads, filename)
            return if (file.exists()) {
                Uri.fromFile(file)
            } else {
                null
            }
        }
    }

    fun getUriForFilenameInCurrentSaveFolder(filename: String, resolver: ContentResolver): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentUri = when (fileType) {
                FileType.JPG, FileType.PNG -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                FileType.ZAINT -> MediaStore.Files.getContentUri("external")
            }
            getUriForFilename(contentUri, filename, resolver, saveRelativePath)
        } else {
            val file = File(File(Environment.getExternalStorageDirectory(), saveRelativePath), filename)
            if (file.exists()) Uri.fromFile(file) else null
        }
    }

    fun checkFileExists(filename: String, resolver: ContentResolver): Boolean {
        return getUriForFilenameInCurrentSaveFolder(filename, resolver) != null
    }

    private fun checkFileExistsInPicturesFolder(filename: String, resolver: ContentResolver): Boolean =
        getUriForFilenameInPicturesFolder(filename, resolver) != null

    private fun checkFileExistsInDownloadsFolder(filename: String, resolver: ContentResolver): Boolean =
        getUriForFilenameInDownloadsFolder(filename, resolver) != null

    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var w = width
        var h = height
        var sampleSize = 1
        while (w > maxWidth || h > maxHeight) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun sampleSizeForImport(resolver: ContentResolver, bitmapUri: Uri): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeBitmapFromUri(resolver, bitmapUri, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Can't load bitmap from uri")
        }
        var sampleSize = 1
        while (bounds.outWidth.toLong() / sampleSize * (bounds.outHeight.toLong() / sampleSize) > MAX_IMPORTED_IMAGE_PIXELS) {
            sampleSize *= 2
        }
        return sampleSize
    }

    @Throws(IOException::class)
    @JvmStatic
    fun getBitmapFromUri(resolver: ContentResolver, bitmapUri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = sampleSizeForImport(resolver, bitmapUri)
        }
        return enableAlpha(decodeBitmapFromUri(resolver, bitmapUri, options))
    }

    @Throws(IOException::class)
    fun getBitmapSampledForSizeFromUri(
        resolver: ContentResolver,
        bitmapUri: Uri,
        context: Context?,
        visibleDrawingSurfaceSize: BitmapSize
    ): BitmapReturnValue {
        if (context == null) {
            return getBitmapReturnValueFromUri(resolver, bitmapUri, null)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeBitmapFromUri(resolver, bitmapUri, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Can't load bitmap from uri")
        }
        val targetSize = DisplayBitmapScaler.calculateTargetSizeForVisibleDrawingSurface(
            bounds.outWidth,
            bounds.outHeight,
            visibleDrawingSurfaceSize.width,
            visibleDrawingSurfaceSize.height
        )
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = maxOf(
                DisplayBitmapScaler.calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSize),
                sampleSizeForImport(resolver, bitmapUri)
            )
        }
        val bitmap = enableAlpha(decodeBitmapFromUri(resolver, bitmapUri, options))
        return BitmapReturnValue(null, bitmap, false)
    }

    fun scaleBitmapToSize(bitmap: Bitmap?, visibleDrawingSurfaceSize: BitmapSize?): Bitmap? {
        if (bitmap == null || visibleDrawingSurfaceSize == null) {
            return bitmap
        }
        val targetSize = DisplayBitmapScaler.calculateTargetSizeForVisibleDrawingSurface(
            bitmap.width,
            bitmap.height,
            visibleDrawingSurfaceSize.width,
            visibleDrawingSurfaceSize.height
        )
        if (targetSize.width == bitmap.width && targetSize.height == bitmap.height) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetSize.width, targetSize.height, true).also {
            bitmap.recycle()
        }
    }

    private fun getMemoryInfo(context: Context?): ActivityManager.MemoryInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager =
            context?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        activityManager?.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    @Throws(IOException::class)
    private fun hasEnoughMemory(
        resolver: ContentResolver,
        bitmapUri: Uri,
        context: Context?
    ): Boolean {
        var scaling = false
        val memoryInfo = getMemoryInfo(context)
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        decodeBitmapFromUri(resolver, bitmapUri, options)
        if (options.outHeight < 0 || options.outWidth < 0) {
            throw IOException("Can't load bitmap from uri")
        }
        val availableMemory = min(
            ((memoryInfo.availMem - memoryInfo.threshold) * CONSTANT_POINT9).toLong(),
            CONSTANT_5000 * CONSTANT_5000 * CONSTANT_4
        )
        val requiredMemory = options.outWidth.toLong() * options.outHeight.toLong() * CONSTANT_4
        if (requiredMemory > availableMemory) {
            scaling = true
        }
        return scaling
    }

    @Throws(IOException::class)
    private fun getScaleFactor(resolver: ContentResolver, bitmapUri: Uri, context: Context?): Int {
        getMemoryInfo(context)
        val options = BitmapFactory.Options()
        decodeBitmapFromUri(resolver, bitmapUri, options)
        if (options.outHeight <= 0 || options.outWidth <= 0) {
            throw IOException("Can't load bitmap from uri")
        }
        val info = Runtime.getRuntime()
        val availableMemory =
            (info.maxMemory() - info.totalMemory() + info.freeMemory()) * CONSTANT_POINT9
        val heightToWidthFactor = options.outWidth / options.outHeight * 1f
        val availablePixels =
            availableMemory / MAX_LAYERS.toFloat() * CONSTANT_POINT9 / CONSTANT_4 // 4 byte per pixel, 10% safety buffer on memory
        val availableHeight = sqrt(availablePixels / heightToWidthFactor)
        val availableWidth = availablePixels / availableHeight
        return calculateSampleSize(
            options.outWidth,
            options.outHeight,
            availableWidth.toInt(),
            availableHeight.toInt()
        )
    }

    @Throws(IOException::class)
    fun getBitmapReturnValueFromUri(
        resolver: ContentResolver,
        bitmapUri: Uri,
        context: Context?
    ): BitmapReturnValue {
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inJustDecodeBounds = false
            inSampleSize = sampleSizeForImport(resolver, bitmapUri)
        }
        val scaling = hasEnoughMemory(resolver, bitmapUri, context) || options.inSampleSize > 1
        val bitmap = enableAlpha(decodeBitmapFromUri(resolver, bitmapUri, options))
        return BitmapReturnValue(
            null,
            bitmap,
            scaling
        )
    }

    @Throws(IOException::class)
    fun getScaledBitmapFromUri(
        resolver: ContentResolver,
        bitmapUri: Uri,
        context: Context?
    ): BitmapReturnValue {
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inJustDecodeBounds = false
            inSampleSize = maxOf(
                getScaleFactor(resolver, bitmapUri, context),
                sampleSizeForImport(resolver, bitmapUri)
            )
        }

        val bitmap = enableAlpha(decodeBitmapFromUri(resolver, bitmapUri, options))
        return BitmapReturnValue(
            null,
            bitmap,
            false
        )
    }

    fun getBitmapFromFile(bitmapFile: File?): Bitmap? {
        bitmapFile ?: return null
        val options = BitmapFactory.Options()
        options.inMutable = true
        return enableAlpha(BitmapFactory.decodeFile(bitmapFile.absolutePath, options))
    }

    @JvmStatic
    fun enableAlpha(bitmap: Bitmap?): Bitmap? {
        bitmap?.setHasAlpha(true)
        return bitmap
    }

    fun saveTemporaryPictureFile(internalMemoryPath: File, commandSerializer: ZaintProjectSerializer) {
        val newFileName = "${TEMP_IMAGE_NAME}1.$ZAINT_RECOVERY_ENDING"
        val tempPath = File(internalMemoryPath, TEMP_IMAGE_DIRECTORY_NAME)
        try {
            tempPath.mkdirs()

            val stream = FileOutputStream("$tempPath/$newFileName")
            commandSerializer.writeToInternalMemory(stream)
            temporaryFilePath = TEMP_IMAGE_TEMP_PATH
        } catch (e: IOException) {
            Log.e("Cannot write", "Can't write to stream", e)
        }
        val oldFile = File(internalMemoryPath, TEMP_IMAGE_PATH)
        if (oldFile.exists()) {
            oldFile.delete()
        }
        val newFile = File(internalMemoryPath, TEMP_IMAGE_TEMP_PATH)
        if (newFile.exists()) {
            newFile.renameTo(File(internalMemoryPath, TEMP_IMAGE_PATH))
            temporaryFilePath = TEMP_IMAGE_PATH
        }
    }

    fun checkForTemporaryFile(internalMemoryPath: File): Boolean {
        val tempPath = File(internalMemoryPath, TEMP_IMAGE_DIRECTORY_NAME)
        if (!tempPath.exists()) {
            return false
        }
        val fileList = tempPath.listFiles()
        if (fileList != null && fileList.isNotEmpty()) {
            if (fileList.size == 2) {
                if (fileList[1].lastModified() > fileList[0].lastModified()) {
                    reorganizeTempFiles(fileList[1], fileList[0], internalMemoryPath)
                } else {
                    reorganizeTempFiles(fileList[0], fileList[1], internalMemoryPath)
                }
            } else {
                temporaryFilePath = fileList[0].path
            }
            return true
        }
        return false
    }

    private fun reorganizeTempFiles(file1: File, file2: File, internalMemoryPath: File) {
        file2.delete()
        file1.renameTo(File(internalMemoryPath, TEMP_IMAGE_PATH))
        temporaryFilePath = TEMP_IMAGE_PATH
    }

    fun openTemporaryPictureFile(internalMemoryPath: File, commandSerializer: ZaintProjectSerializer): WorkspaceReturnValue? {
        var workspaceReturnValue: WorkspaceReturnValue? = null
        val temporaryFile = getTemporaryFile(internalMemoryPath)
        if (temporaryFile != null) {
            try {
                FileInputStream(temporaryFile).use { stream ->
                    workspaceReturnValue = commandSerializer.readFromInternalMemory(stream)
                }
            } catch (e: IOException) {
                Log.e("Cannot read", "Can't read from stream", e)
                discardBrokenTemporaryFile(temporaryFile)
            } catch (e: RuntimeException) {
                Log.e("Cannot read", "Can't restore temporary image", e)
                discardBrokenTemporaryFile(temporaryFile)
            }
        }
        return workspaceReturnValue
    }

    private fun getTemporaryFile(internalMemoryPath: File): File? {
        val path = temporaryFilePath ?: return null
        val file = File(path)
        return if (file.isAbsolute) {
            file
        } else {
            File(internalMemoryPath, path)
        }
    }

    private fun discardBrokenTemporaryFile(temporaryFile: File) {
        temporaryFile.delete()
        temporaryFilePath = null
    }

    fun deleteTempFile(internalMemoryPath: File) {
        val tempPath = File(internalMemoryPath, TEMP_IMAGE_DIRECTORY_NAME)
        tempPath.listFiles()?.forEach { file ->
            file.delete()
        }
        tempPath.delete()
        temporaryFilePath = null
    }
}
