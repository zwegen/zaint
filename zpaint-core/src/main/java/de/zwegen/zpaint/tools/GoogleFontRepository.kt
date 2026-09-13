package de.zwegen.zpaint.tools

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import de.zwegen.zpaint.common.MAX_FONT_BYTES
import de.zwegen.zpaint.common.MAX_FONT_METADATA_BYTES
import de.zwegen.zpaint.common.readBytesWithLimit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

object GoogleFontRepository {
    private const val FONT_FOLDER_NAME = "fonts"
    private const val GOOGLE_FONTS_METADATA_URL = "https://fonts.google.com/metadata/fonts"
    private const val GOOGLE_FONTS_CSS_URL = "https://fonts.googleapis.com/css2?family="
    private const val USER_AGENT = "Android"
    private val fallbackPopularFonts = listOf(
        "Roboto",
        "Open Sans",
        "Lato",
        "Montserrat",
        "Oswald",
        "Raleway",
        "Poppins",
        "Merriweather",
        "Noto Sans",
        "Playfair Display",
        "Nunito",
        "Ubuntu",
        "Rubik",
        "Source Sans 3",
        "Inter",
        "Work Sans",
        "Quicksand",
        "Dancing Script",
        "Pacifico",
        "Bebas Neue"
    )

    @Volatile
    private var cachedFamilies: List<String>? = null
    @Volatile
    private var cachedMetadata: JSONObject? = null

    fun installedFonts(context: Context): List<TextFont> {
        val fonts = linkedMapOf<String, FontFiles>()
        addFontsFromFamilyDirectories(fontRootDirectory(context), fonts)
        return fonts.values
            .mapNotNull { it.toTextFont() }
            .sortedBy { it.displayName.orEmpty().lowercase(Locale.US) }
    }

    fun searchFonts(query: String, offset: Int, limit: Int): List<String> {
        val normalizedQuery = query.trim().lowercase(Locale.US)
        val families = runCatching { loadFamilies() }.getOrElse { fallbackPopularFonts }
        val filtered = if (normalizedQuery.isEmpty()) {
            families
        } else {
            families.filter { it.lowercase(Locale.US).contains(normalizedQuery) }
        }
        return filtered.drop(offset).take(limit)
    }

    fun downloadFont(context: Context, family: String): TextFont {
        val displayName = family.trim()
        val safeName = safeFileName(displayName)
        val variants = runCatching { availableVariants(displayName) }.getOrDefault(emptySet())
        val files = linkedMapOf(
            "$safeName-Regular.ttf" to openBytesConnection(fontUrlFor(displayName, GoogleFontVariant.REGULAR))
        )
        if (GoogleFontVariant.BOLD in variants) {
            files["$safeName-Bold.ttf"] = openBytesConnection(fontUrlFor(displayName, GoogleFontVariant.BOLD))
        }
        if (GoogleFontVariant.ITALIC in variants) {
            files["$safeName-Italic.ttf"] = openBytesConnection(fontUrlFor(displayName, GoogleFontVariant.ITALIC))
        }
        if (GoogleFontVariant.BOLD_ITALIC in variants) {
            files["$safeName-BoldItalic.ttf"] = openBytesConnection(fontUrlFor(displayName, GoogleFontVariant.BOLD_ITALIC))
        }

        val installedDirectory = replaceFontFamily(context, safeName, files)
        val regularUri = Uri.fromFile(File(installedDirectory, "$safeName-Regular.ttf"))
        val boldUri = files["$safeName-Bold.ttf"]?.let { Uri.fromFile(File(installedDirectory, "$safeName-Bold.ttf")) }
        val italicUri = files["$safeName-Italic.ttf"]?.let { Uri.fromFile(File(installedDirectory, "$safeName-Italic.ttf")) }
        val boldItalicUri = files["$safeName-BoldItalic.ttf"]?.let { Uri.fromFile(File(installedDirectory, "$safeName-BoldItalic.ttf")) }
        return TextFont.custom(
            displayName,
            regularUri.toString(),
            boldUri?.toString(),
            italicUri?.toString(),
            boldItalicUri?.toString()
        )
    }

    fun previewTypeface(context: Context, family: String): Typeface? =
        try {
            val directory = File(context.cacheDir, "google-font-previews").apply { mkdirs() }
            val file = File(directory, "${safeFileName(family)}.ttf")
            if (!file.exists()) {
                file.writeBytes(openBytesConnection(fontUrlFor(family, GoogleFontVariant.REGULAR)))
            }
            Typeface.createFromFile(file)
        } catch (_: Exception) {
            null
        }

    fun typefaceFor(context: Context, textFont: TextFont, style: Int): Typeface {
        val resolvedFont = enrichFont(context, textFont)
        val uri = resolvedFont.fileUriForStyle(isBold(style), isItalic(style)) ?: resolvedFont.fileUri
            ?: return Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        return try {
            val parsedUri = Uri.parse(uri)
            if (parsedUri.scheme == "file") {
                Typeface.createFromFile(parsedUri.path)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                    Typeface.Builder(descriptor.fileDescriptor).build()
                } ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
        } catch (_: Exception) {
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }

    fun deleteFont(context: Context, textFont: TextFont): Boolean {
        val uris = listOfNotNull(
            textFont.fileUri,
            textFont.boldFileUri,
            textFont.italicFileUri,
            textFont.boldItalicFileUri
        ).distinct()
        if (uris.isEmpty()) {
            return false
        }
        var deletedAny = false
        uris.forEach { uri ->
            deletedAny = deleteUri(context, uri) || deletedAny
        }
        return deletedAny
    }

    fun enrichFont(context: Context, textFont: TextFont): TextFont {
        if (!textFont.isCustom) {
            return textFont
        }
        return installedFonts(context).firstOrNull { it.displayName == textFont.displayName } ?: textFont
    }

    private fun deleteUri(context: Context, uri: String): Boolean =
        try {
            val parsedUri = Uri.parse(uri)
            if (parsedUri.scheme == "file") {
                parsedUri.path?.let { File(it).delete() } == true
            } else {
                context.contentResolver.delete(parsedUri, null, null) > 0
            }
        } catch (_: Exception) {
            false
        }

    private fun loadFamilies(): List<String> {
        cachedFamilies?.let { return it }
        val metadata = loadMetadata()
        val families = metadata.getJSONArray("familyMetadataList")
        val result = (0 until families.length())
            .map { families.getJSONObject(it) }
            .sortedBy { it.optInt("popularity", Int.MAX_VALUE) }
            .map { it.getString("family") }
        cachedFamilies = result
        return result
    }

    private fun loadMetadata(): JSONObject {
        cachedMetadata?.let { return it }
        val jsonText = openTextConnection(GOOGLE_FONTS_METADATA_URL).removePrefix(")]}'\n")
        return JSONObject(jsonText).also { cachedMetadata = it }
    }

    private fun availableVariants(family: String): Set<GoogleFontVariant> {
        val families = loadMetadata().getJSONArray("familyMetadataList")
        val fonts = (0 until families.length())
            .asSequence()
            .map { families.getJSONObject(it) }
            .firstOrNull { it.getString("family") == family }
            ?.getJSONObject("fonts")
            ?: return emptySet()
        val variants = mutableSetOf<GoogleFontVariant>()
        if (fonts.has("700")) variants += GoogleFontVariant.BOLD
        if (fonts.has("400i")) variants += GoogleFontVariant.ITALIC
        if (fonts.has("700i")) variants += GoogleFontVariant.BOLD_ITALIC
        return variants
    }

    private fun cssUrlFor(family: String, variant: GoogleFontVariant): String {
        val encodedFamily = URLEncoder.encode(family, "UTF-8")
        return GOOGLE_FONTS_CSS_URL + when (variant) {
            GoogleFontVariant.REGULAR -> encodedFamily
            GoogleFontVariant.BOLD -> "$encodedFamily:wght@700"
            GoogleFontVariant.ITALIC -> "$encodedFamily:ital,wght@1,400"
            GoogleFontVariant.BOLD_ITALIC -> "$encodedFamily:ital,wght@1,700"
        }
    }

    private fun fontUrlFor(family: String, variant: GoogleFontVariant): String =
        Regex("""url\((https:[^)]+\.ttf[^)]*)\)""")
            .find(openTextConnection(cssUrlFor(family, variant)))
            ?.groupValues
            ?.get(1)
            ?: error("No TTF font found for $family")

    private fun openTextConnection(url: String): String =
        String(openBytesConnection(url, MAX_FONT_METADATA_BYTES), Charsets.UTF_8)

    private fun openBytesConnection(url: String, maxBytes: Int = MAX_FONT_BYTES): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        if (connection.contentLengthLong > maxBytes) {
            connection.disconnect()
            error("Download is too large")
        }
        return try {
            connection.inputStream.use { it.readBytesWithLimit(maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads are prepared in a sibling directory before the installed font is replaced.
     * A failed download or write therefore keeps the previous font intact.
     */
    private fun replaceFontFamily(context: Context, safeName: String, files: Map<String, ByteArray>): File {
        val rootDirectory = fontRootDirectory(context).apply { mkdirs() }
        val installedDirectory = File(rootDirectory, safeName)
        val stagingDirectory = File(rootDirectory, ".${safeName}-${UUID.randomUUID()}.tmp")
        val backupDirectory = File(rootDirectory, ".${safeName}-${UUID.randomUUID()}.backup")
        try {
            check(stagingDirectory.mkdirs()) { "Could not create font staging directory" }
            files.forEach { (fileName, bytes) -> File(stagingDirectory, fileName).writeBytes(bytes) }

            if (installedDirectory.exists() && !installedDirectory.renameTo(backupDirectory)) {
                error("Could not back up installed font")
            }
            if (!stagingDirectory.renameTo(installedDirectory)) {
                backupDirectory.renameTo(installedDirectory)
                error("Could not install downloaded font")
            }
            backupDirectory.deleteRecursively()
            return installedDirectory
        } finally {
            stagingDirectory.deleteRecursively()
            if (backupDirectory.exists() && installedDirectory.exists()) {
                backupDirectory.deleteRecursively()
            }
        }
    }

    private fun addFontsFromFamilyDirectories(rootDirectory: File, fonts: MutableMap<String, FontFiles>) {
        rootDirectory.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
            ?.forEach { entry ->
                when {
                    entry.isDirectory -> addFontsFromFlatDirectory(entry, fonts, entry.name)
                    entry.isFile && isFontFile(entry.name) -> addFontFile(fonts, entry.name, Uri.fromFile(entry).toString())
                }
            }
    }

    private fun addFontsFromFlatDirectory(
        directory: File,
        fonts: MutableMap<String, FontFiles>,
        familyName: String? = null
    ) {
        directory.listFiles()
            ?.filter { it.isFile && isFontFile(it.name) }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?.forEach { file ->
                addFontFile(fonts, file.name, Uri.fromFile(file).toString(), familyName)
            }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("""[^A-Za-z0-9._ -]"""), "_").trim().ifEmpty { "ZaintFont" }

    private fun isFontFile(name: String): Boolean =
        name.endsWith(".ttf", ignoreCase = true) || name.endsWith(".otf", ignoreCase = true)

    private fun addFontFile(
        fonts: MutableMap<String, FontFiles>,
        fileName: String,
        uri: String,
        familyName: String? = null
    ) {
        val fontFile = parseFontFileName(fileName, familyName)
        val currentFiles = fonts[fontFile.family] ?: FontFiles(fontFile.family)
        fonts[fontFile.family] = currentFiles.withVariant(fontFile.variant, uri)
    }

    private fun parseFontFileName(fileName: String, familyName: String? = null): ParsedFontFile {
        val name = fileName.substringBeforeLast('.')
        val variant = when {
            boldItalicSuffixRegex.containsMatchIn(name) -> GoogleFontVariant.BOLD_ITALIC
            boldSuffixRegex.containsMatchIn(name) -> GoogleFontVariant.BOLD
            italicSuffixRegex.containsMatchIn(name) -> GoogleFontVariant.ITALIC
            else -> GoogleFontVariant.REGULAR
        }
        val family = familyName?.trim()?.takeIf { it.isNotEmpty() }
            ?: variantSuffixRegexes
                .fold(name) { parsedFamilyName, regex -> parsedFamilyName.replace(regex, "") }
                .trim()
                .trim('-', '_')
                .ifEmpty { name }
        return ParsedFontFile(family, variant)
    }

    private fun fontRootDirectory(context: Context): File =
        File(context.filesDir, FONT_FOLDER_NAME)

    private fun fontFamilyDirectory(context: Context, family: String): File =
        File(fontRootDirectory(context), safeFileName(family))

    private val boldItalicSuffixRegex = Regex("""(?i)[\s_-]*bold[\s_-]*italic$""")
    private val boldSuffixRegex = Regex("""(?i)[\s_-]*bold$""")
    private val italicSuffixRegex = Regex("""(?i)[\s_-]*italic$""")
    private val regularSuffixRegex = Regex("""(?i)[\s_-]*regular$""")
    private val variantSuffixRegexes = listOf(
        boldItalicSuffixRegex,
        boldSuffixRegex,
        italicSuffixRegex,
        regularSuffixRegex
    )

    private fun isBold(style: Int): Boolean =
        style == Typeface.BOLD || style == Typeface.BOLD_ITALIC

    private fun isItalic(style: Int): Boolean =
        style == Typeface.ITALIC || style == Typeface.BOLD_ITALIC

    private data class ParsedFontFile(
        val family: String,
        val variant: GoogleFontVariant
    )

    private data class FontFiles(
        val family: String,
        val regularUri: String? = null,
        val boldUri: String? = null,
        val italicUri: String? = null,
        val boldItalicUri: String? = null
    ) {
        fun withVariant(variant: GoogleFontVariant, uri: String): FontFiles =
            when (variant) {
                GoogleFontVariant.REGULAR -> copy(regularUri = uri)
                GoogleFontVariant.BOLD -> copy(boldUri = uri)
                GoogleFontVariant.ITALIC -> copy(italicUri = uri)
                GoogleFontVariant.BOLD_ITALIC -> copy(boldItalicUri = uri)
            }

        fun toTextFont(): TextFont? {
            val regular = regularUri ?: return null
            return TextFont.custom(family, regular, boldUri, italicUri, boldItalicUri)
        }
    }

    private enum class GoogleFontVariant {
        REGULAR,
        BOLD,
        ITALIC,
        BOLD_ITALIC
    }
}
