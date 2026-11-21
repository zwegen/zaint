package de.zwegen.zpaint.iotasks

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.test.espresso.idling.CountingIdlingResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.contract.ZaintLayerContracts
import java.io.IOException
import java.lang.ref.WeakReference

class ZaintDocumentSaveTask(
    activity: SaveImageCallback,
    private val requestCode: Int,
    private val layerModel: ZaintLayerContracts.Model,
    private var uri: Uri?,
    private val saveAsCopy: Boolean,
    private val context: Context,
    private val commandSerializer: ZaintProjectSerializer?,
    private val scopeIO: CoroutineScope,
    private val idlingResource: CountingIdlingResource
) {
    private val callbackRef: WeakReference<SaveImageCallback> = WeakReference(activity)

    companion object {
        private val TAG = ZaintDocumentSaveTask::class.java.simpleName
    }

    private fun getImageUri(
        callback: SaveImageCallback,
        bitmap: Bitmap?,
        filename: String,
        options: ZaintDocumentStorage.ImageSaveOptions
    ): Uri? {
        return if (uri == null) {
            val imageUri = ZaintDocumentStorage.saveBitmapToFile(filename, bitmap, callback.contentResolver, context, options)
            imageUri
        } else {
            uri?.let { ZaintDocumentStorage.saveBitmapToUri(it, bitmap, context, options) }
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    fun execute() {
        val callback = callbackRef.get() ?: return
        if (callback.isFinishing) return
        val saveState = callback.captureSaveState()
        val fileType = ZaintDocumentStorage.fileType
        val filename = ZaintDocumentStorage.defaultFileName
        val imageOptions = ZaintDocumentStorage.captureImageSaveOptions()
        callback.onSaveImagePreExecute(requestCode)

        // Bind the actual pixels/layers to this request before another document can replace them.
        // Disk I/O remains asynchronous; snapshots are released even when a save fails or cancels.
        var preparedProject: ZaintProjectSerializer.PreparedSave? = null
        var preparedBitmap: Bitmap? = null
        try {
            synchronized(layerModel) {
                if (fileType == ZaintDocumentStorage.FileType.ZAINT) {
                    preparedProject = (commandSerializer
                        ?: throw IOException("Zaint project serializer is unavailable")).prepareSave()
                } else {
                    preparedBitmap = layerModel.getBitmapOfAllLayers()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can't capture image for saving", e)
            callback.onSaveImagePostExecute(requestCode, null, saveAsCopy, saveState)
            return
        }

        idlingResource.increment()
        val saveJob = scopeIO.launch {
            var currentUri: Uri? = null
            try {
                currentUri = if (fileType == ZaintDocumentStorage.FileType.ZAINT) {
                    val project = requireNotNull(preparedProject)
                    if (uri == null) project.writeToFile(filename)
                    else project.overWriteFile(filename, requireNotNull(uri), callback.contentResolver)
                } else {
                    getImageUri(callback, preparedBitmap, filename, imageOptions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Can't save image file", e)
            }

            withContext(Dispatchers.Main) {
                if (!callback.isFinishing) {
                    callback.onSaveImagePostExecute(requestCode, currentUri, saveAsCopy, saveState)
                }
            }
        }
        saveJob.invokeOnCompletion {
            preparedProject?.close()
            preparedBitmap?.let { if (!it.isRecycled) it.recycle() }
            idlingResource.decrement()
        }
    }

    data class SaveState(
        val documentId: Long,
        val revision: Long,
        val timelineRevision: Long,
        val wasIdle: Boolean
    )

    interface SaveImageCallback {
        val contentResolver: ContentResolver
        val isFinishing: Boolean
        fun captureSaveState(): SaveState? = null
        fun onSaveImagePreExecute(requestCode: Int)
        fun onSaveImagePostExecute(requestCode: Int, uri: Uri?, saveAsCopy: Boolean)
        fun onSaveImagePostExecute(requestCode: Int, uri: Uri?, saveAsCopy: Boolean, saveState: SaveState?) {
            onSaveImagePostExecute(requestCode, uri, saveAsCopy)
        }
    }
}
