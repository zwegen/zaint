package de.zwegen.zpaint.tools

data class TextFont(
    val builtInFont: ZaintFontFamily? = null,
    val displayName: String? = null,
    val fileUri: String? = null,
    val boldFileUri: String? = null,
    val italicFileUri: String? = null,
    val boldItalicFileUri: String? = null
) {
    val isCustom: Boolean
        get() = fileUri != null

    val id: String
        get() = builtInFont?.name ?: "custom:${displayName.orEmpty()}"

    val supportsBold: Boolean
        get() = builtInFont?.supportsBold ?: (boldFileUri != null)

    val supportsItalic: Boolean
        get() = builtInFont?.supportsItalic ?: (italicFileUri != null)

    fun supportsStyle(bold: Boolean, italic: Boolean): Boolean =
        builtInFont?.supportsStyle(bold, italic) ?: when {
            bold && italic -> boldItalicFileUri != null
            bold -> boldFileUri != null
            italic -> italicFileUri != null
            else -> fileUri != null
        }

    fun fileUriForStyle(bold: Boolean, italic: Boolean): String? =
        when {
            bold && italic -> boldItalicFileUri
            bold -> boldFileUri
            italic -> italicFileUri
            else -> fileUri
        }

    companion object {
        fun builtIn(fontType: ZaintFontFamily) = TextFont(builtInFont = fontType)

        fun custom(
            displayName: String,
            fileUri: String,
            boldFileUri: String? = null,
            italicFileUri: String? = null,
            boldItalicFileUri: String? = null
        ) = TextFont(
            displayName = displayName,
            fileUri = fileUri,
            boldFileUri = boldFileUri,
            italicFileUri = italicFileUri,
            boldItalicFileUri = boldItalicFileUri
        )
    }
}
