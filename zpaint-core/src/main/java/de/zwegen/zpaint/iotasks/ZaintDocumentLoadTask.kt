package de.zwegen.zpaint.iotasks

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.test.espresso.idling.CountingIdlingResource
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.common.MAX_SVG_BYTES
import de.zwegen.zpaint.common.readBytesWithLimit
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.common.LOAD_IMAGE_DEFAULT
import de.zwegen.zpaint.tools.implementation.SvgBitmapRenderer

class ZaintDocumentLoadTask(
    callback: LoadImageCallback,
    private val requestCode: Int,
    private val uri: Uri?,
    context: Context,
    private val scaleImage: Boolean,
    private val commandSerializer: ZaintProjectSerializer,
    private val visibleDrawingSurfaceSize: BitmapSize? = null,
    private val scopeIO: CoroutineScope,
    private val idlingResource: CountingIdlingResource
) {
    private val callbackRef: WeakReference<LoadImageCallback> = WeakReference(callback)
    private val context: WeakReference<Context> = WeakReference(context)

    private fun getMimeType(uri: Uri, resolver: ContentResolver): String? {
        if (ZaintProjectFormat.isZaintProject(uri, resolver)) {
            return ZaintProjectFormat.MIME_TYPE
        }
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            return resolver.getType(uri)
        }
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase(Locale.US))
    }

    private fun getBitmapReturnValue(
        uri: Uri,
        resolver: ContentResolver
    ): BitmapReturnValue {
        val mimeType: String? = getMimeType(uri, resolver)
        val result = if (isSvg(uri, mimeType, resolver)) {
            getSvgBitmapReturnValue(uri, resolver)
        } else if (ZaintProjectFormat.isZaintProject(uri, resolver)) {
            val fileContent = commandSerializer.readFromFile(uri)
            BitmapReturnValue(fileContent.commandModel, fileContent.colorHistory)
        } else {
            if (scaleImage) {
                ZaintDocumentStorage.getScaledBitmapFromUri(resolver, uri, context.get())
            } else if (requestCode == LOAD_IMAGE_DEFAULT && visibleDrawingSurfaceSize != null) {
                ZaintDocumentStorage.getBitmapSampledForSizeFromUri(
                    resolver,
                    uri,
                    context.get(),
                    visibleDrawingSurfaceSize
                )
            } else {
                ZaintDocumentStorage.getBitmapReturnValueFromUri(resolver, uri, context.get())
            }
        }
        return result
    }

    private fun isSvg(uri: Uri, mimeType: String?, resolver: ContentResolver): Boolean {
        if (mimeType == "image/svg+xml" || uri.toString().lowercase(Locale.US).substringBefore("?").endsWith(".svg")) {
            return true
        }
        if (mimeType != null && mimeType != "application/octet-stream" && mimeType != "text/plain" && mimeType != "application/xml") {
            return false
        }
        return resolver.openInputStream(uri)?.use(SvgContentDetector::isSvg) ?: false
    }

    @Throws(IOException::class)
    private fun getSvgBitmapReturnValue(uri: Uri, resolver: ContentResolver): BitmapReturnValue {
        val svgText = resolver.openInputStream(uri)?.use { inputStream ->
            String(inputStream.readBytesWithLimit(MAX_SVG_BYTES), Charsets.UTF_8)
        } ?: throw IOException("Can't open SVG input stream")
        val bitmap = try {
            SvgBitmapRenderer().render(svgText)
        } catch (exception: Exception) {
            throw IOException("Can't render SVG file", exception)
        }
        return BitmapReturnValue(null, bitmap, false)
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    fun execute() {
        val callback = callbackRef.get()
        if (callback == null || callback.isFinishing) {
            return
        }
        callback.onLoadImagePreExecute(requestCode)

        var returnValue: BitmapReturnValue? = null
        scopeIO.launch {
            idlingResource.increment()
            try {
                if (uri == null) {
                    Log.e(TAG, "Can't load image file, uri is null")
                } else {
                    try {
                        val resolver = callback.contentResolver
                        ZaintDocumentStorage.filename = "image"
                        returnValue = getBitmapReturnValue(uri, resolver)
                    } catch (e: IOException) {
                        Log.e(TAG, "Can't load image file", e)
                    } catch (e: NullPointerException) {
                        Log.e(TAG, "Can't load image file", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!callback.isFinishing) {
                        callback.onLoadImagePostExecute(requestCode, uri, returnValue)
                    }
                }
            } finally {
                idlingResource.decrement()
            }
        }
    }

    interface LoadImageCallback {
        fun onLoadImagePostExecute(requestCode: Int, uri: Uri?, result: BitmapReturnValue?)
        fun onLoadImagePreExecute(requestCode: Int)
        val contentResolver: ContentResolver
        val isFinishing: Boolean
    }

    companion object {
        private val TAG = ZaintDocumentLoadTask::class.java.simpleName
    }
}
