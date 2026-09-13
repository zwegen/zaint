package de.zwegen.zpaint.tools

import androidx.annotation.StringRes
import de.zwegen.zpaint.R

/** Zaint's built-in font catalog and its available resource variants. */
enum class ZaintFontFamily(
    @StringRes val label: Int,
    private val regular: Int? = null,
    private val bold: Int? = null,
    private val italic: Int? = null,
    private val boldItalic: Int? = null,
    private val shownInPicker: Boolean = true
) {
    INTER(
        R.string.text_tool_dialog_font_inter,
        R.font.font_inter_regular,
        R.font.font_inter_bold,
        R.font.font_inter_italic,
        R.font.font_inter_bold_italic
    ),
    SOURCE_SERIF(
        R.string.text_tool_dialog_font_source_serif,
        R.font.font_source_serif_regular,
        R.font.font_source_serif_bold,
        R.font.font_source_serif_italic,
        R.font.font_source_serif_bold_italic
    ),
    CAVEAT(R.string.text_tool_dialog_font_caveat, R.font.font_caveat_regular),
    BANGERS(R.string.text_tool_dialog_font_bangers, R.font.font_bangers_regular),
    SANS_SERIF(R.string.text_tool_dialog_font_sans_serif, shownInPicker = false),
    SERIF(R.string.text_tool_dialog_font_serif, shownInPicker = false),
    MONOSPACE(R.string.text_tool_dialog_font_monospace, shownInPicker = false);

    val supportsBold: Boolean
        get() = bold != null

    val supportsItalic: Boolean
        get() = italic != null

    fun resourceFor(isBold: Boolean, isItalic: Boolean): Int? =
        when {
            isBold && isItalic -> boldItalic ?: bold ?: italic ?: regular
            isBold -> bold ?: regular
            isItalic -> italic ?: regular
            else -> regular
        }

    fun supports(isBold: Boolean, isItalic: Boolean): Boolean =
        resourceFor(isBold, isItalic) != null

    fun supportsStyle(isBold: Boolean, isItalic: Boolean): Boolean =
        when {
            isBold && isItalic -> boldItalic != null
            isBold -> bold != null
            isItalic -> italic != null
            else -> regular != null
        }

    fun fontResourceFor(isBold: Boolean, isItalic: Boolean): Int? =
        resourceFor(isBold, isItalic)

    companion object {
        fun pickerFamilies(): List<ZaintFontFamily> = values().filter { it.shownInPicker }

        fun savedNameOrDefault(savedName: String?, default: ZaintFontFamily = INTER): ZaintFontFamily =
            values().firstOrNull { it.name == savedName } ?: default
    }
}
