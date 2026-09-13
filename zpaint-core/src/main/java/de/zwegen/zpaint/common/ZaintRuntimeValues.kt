package de.zwegen.zpaint.common

import android.os.Environment
import java.io.File

// Names used only for Zaint-created working files and document recovery.
const val TEMP_PICTURE_NAME = "zaintTemp"
const val ZAINT_RECOVERY_ENDING = "zaint-recovery"
const val TEMP_IMAGE_DIRECTORY_NAME = "TemporaryImages"
const val TEMP_IMAGE_NAME = "temporaryImage"
const val TEMP_IMAGE_PATH = "$TEMP_IMAGE_DIRECTORY_NAME/$TEMP_IMAGE_NAME.$ZAINT_RECOVERY_ENDING"
const val TEMP_IMAGE_TEMP_PATH = "$TEMP_IMAGE_DIRECTORY_NAME/${TEMP_IMAGE_NAME}1.$ZAINT_RECOVERY_ENDING"

// Stable fragment keys for the dialogs that belong to the current Zaint UI.
const val ABOUT_DIALOG_FRAGMENT_TAG = "aboutdialogfragment"
const val SAVE_DIALOG_FRAGMENT_TAG = "savedialogerror"
const val LOAD_DIALOG_FRAGMENT_TAG = "loadbitmapdialogerror"
const val COLOR_PICKER_DIALOG_TAG = "ColorPickerDialogTag"
const val SAVE_QUESTION_FRAGMENT_TAG = "savebeforequitfragment"
const val SAVE_INFORMATION_DIALOG_TAG = "saveinformationdialogfragment"
const val OVERWRITE_INFORMATION_DIALOG_TAG = "overwriteinformationdialogfragment"
const val PNG_INFORMATION_DIALOG_TAG = "pnginformationdialogfragment"
const val JPG_INFORMATION_DIALOG_TAG = "jpginformationdialogfragment"
const val PERMISSION_DIALOG_FRAGMENT_TAG = "permissiondialogfragment"
const val SCALE_IMAGE_FRAGMENT_TAG = "showscaleimagedialog"
const val INDETERMINATE_PROGRESS_DIALOG_TAG = "indeterminateprogressdialogfragment"

const val INVALID_RESOURCE_ID = 0
const val DEFAULT_CANVAS_WIDTH = 1080
const val DEFAULT_CANVAS_HEIGHT = 1080
const val MAX_LAYERS = 100
const val MEGABYTE_IN_BYTE = 1_048_576L
const val MINIMUM_HEAP_SPACE_FOR_NEW_LAYER = 40
const val ITALIC_FONT_BOX_ADJUSTMENT = 1.2f
const val SPECIFIC_FILETYPE_SHARED_PREFERENCES_NAME = "Ownfiletypepreferences"
const val ANIMATION_DURATION: Long = 250

object ZaintStorageDirectories {
    @JvmField
    val pictures = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES)

    @JvmField
    val downloads = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
}
