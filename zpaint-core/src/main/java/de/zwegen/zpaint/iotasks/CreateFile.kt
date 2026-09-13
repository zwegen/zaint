package de.zwegen.zpaint.iotasks

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.zwegen.zpaint.ZaintDocumentStorage
import java.io.File
import java.lang.NullPointerException
import java.lang.ref.WeakReference

/** Creates an empty image off the UI thread and reports the result only to a live host. */
class CreateFile(
    callback: CreateFileCallback,
    private val requestCode: Int,
    private val filename: String?,
    private val scopeIO: CoroutineScope
) {
    private val callbackRef: WeakReference<CreateFileCallback> = WeakReference(callback)

    @SuppressWarnings("TooGenericExceptionCaught")
    fun execute() {
        scopeIO.launch {
            val callback = callbackRef.get()
            val file = createFile(callback)
            withContext(Dispatchers.Main) {
                if (callback != null && !callback.isFinishing) {
                    callback.onCreateFilePostExecute(requestCode, file)
                }
            }
        }
    }

    private fun createFile(callback: CreateFileCallback?): File? = try {
        ZaintDocumentStorage.createNewEmptyPictureFile(filename, callback?.fileActivity)
    } catch (error: NullPointerException) {
        Log.e(TAG, "Can't create file", error)
        null
    }

    interface CreateFileCallback {
        val fileActivity: Activity?
        val isFinishing: Boolean
        fun onCreateFilePostExecute(requestCode: Int, file: File?)
    }

    companion object {
        private val TAG = CreateFile::class.java.simpleName
    }
}
