package de.zwegen.zpaint.iotasks

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** The one project-file format owned by Zaint. */
object ZaintProjectFormat {
    /** Extension used for newly saved Zaint projects. */
    const val EXTENSION = ".znt"
    const val MIME_TYPE = "application/x-znt"

    /** Kept so projects saved by older Zaint versions remain openable. */
    const val LEGACY_EXTENSION = ".zaint"
    const val LEGACY_MIME_TYPE = "application/x-zaint"

    fun isZaintFileName(fileName: String?): Boolean =
        fileName?.lowercase(Locale.US)?.let {
            it.endsWith(EXTENSION) || it.endsWith(LEGACY_EXTENSION)
        } == true

    fun isZaintProject(uri: Uri, resolver: ContentResolver): Boolean =
        isZaintFileName(displayName(uri, resolver) ?: uri.lastPathSegment)

    private fun displayName(uri: Uri, resolver: ContentResolver): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex < 0) null else cursor.getString(columnIndex)
            }
        }.getOrNull()
    }
}
