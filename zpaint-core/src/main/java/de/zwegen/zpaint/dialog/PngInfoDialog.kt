package de.zwegen.zpaint.dialog

import de.zwegen.zpaint.R

/** Explains Zaint's PNG export option. */
class PngInfoDialog : ImageFormatInfoDialog() {
    override val titleResource = R.string.zpaint_png_title_dialog
    override val messageResource = R.string.zpaint_png_message_dialog
}
