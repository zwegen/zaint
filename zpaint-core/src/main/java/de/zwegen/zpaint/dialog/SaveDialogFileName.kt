package de.zwegen.zpaint.dialog

import de.zwegen.zpaint.ZaintDocumentStorage

internal class SaveDialogFileName(
    sourceFolderName: String?,
    val editableFilename: String,
    hasConcreteTarget: Boolean
) {
    val folderPrefix: String = "${displayFolderName(sourceFolderName, hasConcreteTarget)}: "

    fun applyToFileIo() {
        ZaintDocumentStorage.filename = editableFilename
    }

    private fun displayFolderName(sourceFolderName: String?, hasConcreteTarget: Boolean): String {
        if (sourceFolderName == null) {
            return DEFAULT_FOLDER_NAME
        }
        return sourceFolderName
            .takeIf { it.isNotBlank() && it.none { character -> character == '/' || character == ':' } }
            ?: if (hasConcreteTarget) UNKNOWN_FOLDER_NAME else DEFAULT_FOLDER_NAME
    }

    private companion object {
        const val DEFAULT_FOLDER_NAME = "Pictures"
        const val UNKNOWN_FOLDER_NAME = "File"
    }
}
