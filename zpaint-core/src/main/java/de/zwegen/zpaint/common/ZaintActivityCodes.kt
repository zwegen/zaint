package de.zwegen.zpaint.common

import androidx.annotation.IntDef

/*
 * Stable codes exchanged by Zaint's editor, document picker and permission
 * callbacks. Values are deliberately retained so an in-flight Android result
 * is always routed to the same editor action after an app update.
 */
const val SAVE_IMAGE_DEFAULT = 1
const val SAVE_IMAGE_NEW_EMPTY = 2
const val SAVE_IMAGE_LOAD_NEW = 3
const val SAVE_IMAGE_FINISH = 4

const val LOAD_IMAGE_DEFAULT = 1
const val LOAD_IMAGE_IMPORT_PNG = 2
const val LOAD_IMAGE_FILL = 3

const val CREATE_FILE_DEFAULT = 1

const val REQUEST_CODE_IMPORT_PNG = 1
const val REQUEST_CODE_LOAD_PICTURE = 2
const val REQUEST_CODE_CREATE_SAVE_FILE = 3
const val REQUEST_CODE_FILL_IMAGE = 4

const val PERMISSION_EXTERNAL_STORAGE_SAVE = 1
const val PERMISSION_EXTERNAL_STORAGE_SAVE_COPY = 2
const val PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW = 3
const val PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY = 4
const val PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH = 5
const val PERMISSION_REQUEST_CODE_REPLACE_PICTURE = 6
const val PERMISSION_REQUEST_CODE_IMPORT_PICTURE = 7

object ZaintActivityCodes {
    @IntDef(SAVE_IMAGE_DEFAULT, SAVE_IMAGE_NEW_EMPTY, SAVE_IMAGE_LOAD_NEW, SAVE_IMAGE_FINISH)
    @Retention(AnnotationRetention.SOURCE)
    annotation class SaveImageRequestCode

    @IntDef(LOAD_IMAGE_DEFAULT, LOAD_IMAGE_IMPORT_PNG, LOAD_IMAGE_FILL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class LoadImageRequestCode

    @IntDef(CREATE_FILE_DEFAULT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class CreateFileRequestCode

    @IntDef(REQUEST_CODE_IMPORT_PNG, REQUEST_CODE_LOAD_PICTURE, REQUEST_CODE_CREATE_SAVE_FILE, REQUEST_CODE_FILL_IMAGE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ActivityRequestCode

    @IntDef(
        PERMISSION_EXTERNAL_STORAGE_SAVE,
        PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
        PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW,
        PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY,
        PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH,
        PERMISSION_REQUEST_CODE_REPLACE_PICTURE,
        PERMISSION_REQUEST_CODE_IMPORT_PICTURE
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class PermissionRequestCode
}
