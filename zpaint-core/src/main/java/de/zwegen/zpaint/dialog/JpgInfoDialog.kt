package de.zwegen.zpaint.dialog

import de.zwegen.zpaint.R

/** Explains Zaint's JPEG export option. */
class JpgInfoDialog : ImageFormatInfoDialog() {
    override val titleResource = R.string.zpaint_jpg_title_dialog
    override val messageResource = R.string.zpaint_jpg_message_dialog
}
