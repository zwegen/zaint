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
        bitmap: Bitmap?
    ): Uri? {
        val filename = ZaintDocumentStorage.defaultFileName
        return if (uri == null) {
            val imageUri = ZaintDocumentStorage.saveBitmapToFile(filename, bitmap, callback.contentResolver, context)
            imageUri
        } else {
            uri?.let { ZaintDocumentStorage.saveBitmapToUri(it, bitmap, context) }
        }
    }

    private fun saveZaintProject(callback: SaveImageCallback): Uri? {
        val fileName = ZaintDocumentStorage.defaultFileName
        val serializer = commandSerializer ?: throw IOException("Zaint project serializer is unavailable")
        return if (uri == null) {
            serializer.writeToFile(fileName)
        } else {
            serializer.overWriteFile(fileName, requireNotNull(uri), callback.contentResolver)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    fun execute() {
        val callback = callbackRef.get()
        if (callback == null || callback.isFinishing) {
            return
        } else {
            callback.onSaveImagePreExecute(requestCode)
        }

        var currentUri: Uri? = null
        scopeIO.launch {
            try {
                idlingResource.increment()
                currentUri = when (ZaintDocumentStorage.fileType) {
                    ZaintDocumentStorage.FileType.ZAINT -> saveZaintProject(callback)
                    else -> getImageUri(callback, layerModel.getBitmapOfAllLayers())
                }
                idlingResource.decrement()
            } catch (e: Exception) {
                idlingResource.decrement()
                when (e) {
                    is IOException -> Log.d(TAG, "Can't save image file", e)
                    is NullPointerException -> Log.e(TAG, "Can't load image file", e)
                }
            }

            withContext(Dispatchers.Main) {
                if (!callback.isFinishing) {
                    callback.onSaveImagePostExecute(requestCode, currentUri, saveAsCopy)
                }
            }
        }
    }

    interface SaveImageCallback {
        val contentResolver: ContentResolver
        val isFinishing: Boolean
        fun onSaveImagePreExecute(requestCode: Int)
        fun onSaveImagePostExecute(requestCode: Int, uri: Uri?, saveAsCopy: Boolean)
    }
}
