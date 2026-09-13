package de.zwegen.zpaint.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.R
import de.zwegen.zpaint.colorpicker.ColorPickerDialog
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.implementation.ZaintCommandFactory
import de.zwegen.zpaint.common.ABOUT_DIALOG_FRAGMENT_TAG
import de.zwegen.zpaint.common.COLOR_PICKER_DIALOG_TAG
import de.zwegen.zpaint.common.OVERWRITE_INFORMATION_DIALOG_TAG
import de.zwegen.zpaint.common.PNG_INFORMATION_DIALOG_TAG
import de.zwegen.zpaint.common.JPG_INFORMATION_DIALOG_TAG
import de.zwegen.zpaint.common.INDETERMINATE_PROGRESS_DIALOG_TAG
import de.zwegen.zpaint.common.SAVE_DIALOG_FRAGMENT_TAG
import de.zwegen.zpaint.common.LOAD_DIALOG_FRAGMENT_TAG
import de.zwegen.zpaint.common.PERMISSION_DIALOG_FRAGMENT_TAG
import de.zwegen.zpaint.common.SAVE_QUESTION_FRAGMENT_TAG
import de.zwegen.zpaint.common.SCALE_IMAGE_FRAGMENT_TAG
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_COPY
import de.zwegen.zpaint.common.SAVE_INFORMATION_DIALOG_TAG
import de.zwegen.zpaint.common.ZaintActivityCodes.ActivityRequestCode
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.dialog.ZaintOverwriteConfirmationDialog
import de.zwegen.zpaint.dialog.PngInfoDialog
import de.zwegen.zpaint.dialog.JpgInfoDialog
import de.zwegen.zpaint.dialog.IndeterminateProgressDialog
import de.zwegen.zpaint.dialog.ZaintErrorNoticeDialog
import de.zwegen.zpaint.dialog.ZaintPermissionSettingsDialog
import de.zwegen.zpaint.dialog.ZaintStoragePermissionDialog
import de.zwegen.zpaint.dialog.ZaintUnsavedChangesDialog
import de.zwegen.zpaint.dialog.ZaintImageScaleDialog
import de.zwegen.zpaint.dialog.ZaintStoragePermissionDialog.StoragePermissionRequest
import de.zwegen.zpaint.dialog.ZaintSaveDialog
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.implementation.ZaintBrushTool

/**
 * Android navigation boundary for Zaint's editor: dialogs, system pickers,
 * permissions, and media notifications remain outside the presenter.
 */
class ZaintEditorNavigator(
    private val host: ZaintEditorNavigationHost,
    private val toolReference: ToolReference
) : ZaintEditorContracts.Navigator {
    override val isSdkAboveOrEqualM: Boolean
        get() = true
    override val isSdkAboveOrEqualQ: Boolean
        @SuppressLint("AnnotateVersionCheck")
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override val isSdkAboveOrEqualT: Boolean
        @SuppressLint("AnnotateVersionCheck")
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private var commandFactory: ZaintCommandFactoryApi = ZaintCommandFactory()

    private fun showDialogFragmentSafely(dialog: DialogFragment, tag: String) {
        val fragmentManager = host.activity.supportFragmentManager
        if (!fragmentManager.isStateSaved) {
            dialog.show(fragmentManager, tag)
        }
    }

    private fun findFragmentByTag(tag: String): Fragment? =
        host.activity.supportFragmentManager.findFragmentByTag(tag)

    private fun setupColorPickerDialogListeners(dialog: ColorPickerDialog) {
        dialog.addOnColorPickedListener(object : OnColorPickedListener {
            override fun colorChanged(color: Int) {
                val command = commandFactory.createColorChangedCommand(toolReference.tool, host, color)
                host.model.colorHistory.addColor(color)

                host.commandManager.addCommandWithoutUndo(command)
            }
        })

        dialog.setOnCanvasPipetteRequested { host.selectPipetteColor() }
    }

    private fun getFileName(uri: Uri?): String? {
        uri ?: return null
        var result: String? = null
        if (uri.scheme == "content") {
            val queryCursor = host.activity.contentResolver.query(uri, null, null, null, null)
            queryCursor.use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    override fun showColorPickerDialog() {
        if (findFragmentByTag(COLOR_PICKER_DIALOG_TAG) == null) {
            toolReference.tool?.let {
                val dialog = ColorPickerDialog.newInstance(
                    it.drawPaint.color,
                    host.model.colorHistory,
                    (it as? ZaintBrushTool)?.preservesTransparencyWhenSelectingAColor() == true
                )
                setupColorPickerDialogListeners(dialog)
                showDialogFragmentSafely(dialog, COLOR_PICKER_DIALOG_TAG)
            }
        }
    }

    override fun startLoadImageActivity(@ActivityRequestCode requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putPicturesInitialUriIfSupported()
        }
        host.activity.startActivityForResult(intent, requestCode)
    }

    override fun startImportImageActivity(@ActivityRequestCode requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        }
        host.activity.startActivityForResult(intent, requestCode)
    }

    override fun startCreateSaveDocumentActivity(
        @ActivityRequestCode requestCode: Int,
        filename: String,
        mimeType: String
    ) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(Intent.EXTRA_TITLE, filename)
            putPicturesInitialUriIfSupported()
        }
        host.activity.startActivityForResult(intent, requestCode)
    }

    private fun Intent.putPicturesInitialUriIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:${Environment.DIRECTORY_PICTURES}"
                )
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * host.activity.resources.displayMetrics.density).toInt()


    override fun startShareImageActivity(bitmap: Bitmap?) {
        val removeMetadata = ZaintDocumentStorage.removeMetadata
        val uri = try {
            ZaintDocumentStorage.removeMetadata = true
            ZaintDocumentStorage.saveBitmapToCache(bitmap, host.activity, "image")
        } finally {
            ZaintDocumentStorage.removeMetadata = removeMetadata
        }
        if (uri == null) {
            return
        }
        val shareIntent = Intent().apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            setDataAndType(uri, host.activity.contentResolver.getType(uri))
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            action = Intent.ACTION_SEND
        }
        val chooserTitle = host.activity.resources.getString(R.string.share_image_via_text)
        host.activity.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    override fun showOverwriteDialog(permissionCode: Int, isExport: Boolean) {
        host.idlingResource.increment()
        val overwriteDialog = ZaintOverwriteConfirmationDialog.forSaveTarget(
            permissionCode,
            isExport
        )
        overwriteDialog.show(
            host.activity.supportFragmentManager,
            OVERWRITE_INFORMATION_DIALOG_TAG
        )
        host.idlingResource.decrement()
    }

    override fun showPngInformationDialog() {
        val pngInfoDialog = PngInfoDialog()
        pngInfoDialog.show(
            host.activity.supportFragmentManager,
            PNG_INFORMATION_DIALOG_TAG
        )
    }

    override fun showJpgInformationDialog() {
        val jpgInfoDialog = JpgInfoDialog()
        jpgInfoDialog.show(
            host.activity.supportFragmentManager,
            JPG_INFORMATION_DIALOG_TAG
        )
    }

    override fun showIndeterminateProgressDialog() {
        val progressDialogFragment = IndeterminateProgressDialog()
        showDialogFragmentSafely(progressDialogFragment, INDETERMINATE_PROGRESS_DIALOG_TAG)
    }

    override fun dismissIndeterminateProgressDialog() {
        val progressDialogFragment =
            findFragmentByTag(INDETERMINATE_PROGRESS_DIALOG_TAG) as DialogFragment?
        progressDialogFragment?.dismiss()
    }

    override fun showToast(resId: Int, duration: Int) {
        // Toasts are intentionally disabled throughout the editor.
    }

    override fun showToast(msg: String, duration: Int) {
        // Toasts are intentionally disabled throughout the editor.
    }

    override fun showSaveErrorDialog() {
        val dialog: AppCompatDialogFragment = ZaintErrorNoticeDialog.forError(
            ZaintErrorNoticeDialog.EditorError.SAVE_FAILURE
        )
        showDialogFragmentSafely(dialog, SAVE_DIALOG_FRAGMENT_TAG)
    }

    override fun showLoadErrorDialog() {
        val dialog: AppCompatDialogFragment = ZaintErrorNoticeDialog.forError(
            ZaintErrorNoticeDialog.EditorError.LOAD_FAILURE
        )
        showDialogFragmentSafely(dialog, LOAD_DIALOG_FRAGMENT_TAG)
    }

    override fun showRequestPermissionRationaleDialog(
        permissionType: StoragePermissionRequest,
        permissions: Array<String>,
        requestCode: Int
    ) {
        val dialog: AppCompatDialogFragment =
            ZaintStoragePermissionDialog.forRequest(permissionType, permissions, requestCode)
        showDialogFragmentSafely(dialog, PERMISSION_DIALOG_FRAGMENT_TAG)
    }

    override fun showRequestPermanentlyDeniedPermissionRationaleDialog() {
        val dialog: AppCompatDialogFragment = ZaintPermissionSettingsDialog.forApplication(
            host.activity.packageName
        )
        showDialogFragmentSafely(dialog, PERMISSION_DIALOG_FRAGMENT_TAG)
    }

    override fun askForPermission(permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(host.activity, permissions, requestCode)
    }

    override fun doIHavePermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            host.activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED

    override fun isPermissionPermanentlyDenied(permissions: Array<String>): Boolean =
        !ActivityCompat.shouldShowRequestPermissionRationale(host.activity, permissions[0])

    override fun finishActivity() {
        host.activity.finishAndRemoveTask()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(0)
        }, 200L)
    }

    override fun showSaveBeforeFinishDialog() {
        val dialog: AppCompatDialogFragment = ZaintUnsavedChangesDialog.forDestination(
            ZaintUnsavedChangesDialog.UnsavedChangesDestination.EXIT
        )
        showDialogFragmentSafely(dialog, SAVE_QUESTION_FRAGMENT_TAG)
    }

    override fun showSaveBeforeNewImageDialog() {
        val dialog: AppCompatDialogFragment = ZaintUnsavedChangesDialog.forDestination(
            ZaintUnsavedChangesDialog.UnsavedChangesDestination.NEW_IMAGE
        )
        showDialogFragmentSafely(dialog, SAVE_QUESTION_FRAGMENT_TAG)
    }

    override fun showSaveBeforeLoadImageDialog() {
        val dialog: AppCompatDialogFragment = ZaintUnsavedChangesDialog.forDestination(
            ZaintUnsavedChangesDialog.UnsavedChangesDestination.LOAD_IMAGE
        )
        showDialogFragmentSafely(dialog, SAVE_QUESTION_FRAGMENT_TAG)
    }

    override fun showScaleImageRequestDialog(uri: Uri?, requestCode: Int) {
        uri ?: return
        val dialog: AppCompatDialogFragment = ZaintImageScaleDialog.forImage(uri, requestCode)
        showDialogFragmentSafely(dialog, SCALE_IMAGE_FRAGMENT_TAG)
    }

    @SuppressLint("VisibleForTests")
    override fun showSaveImageInformationDialogWhenStandalone(
        permissionCode: Int,
        imageNumber: Int,
        isExport: Boolean
    ) {
        val uri = host.model.savedPictureUri
        if (uri != null && permissionCode != PERMISSION_EXTERNAL_STORAGE_SAVE_COPY) {
            ZaintDocumentStorage.parseFileName(uri, host.activity.contentResolver)
        }
        var isStandard = false
        if (permissionCode == PERMISSION_EXTERNAL_STORAGE_SAVE_COPY) {
            isStandard = true
        }
        val saveInfoDialog =
            ZaintSaveDialog.forSave(permissionCode, imageNumber, isStandard, isExport, uri)
        saveInfoDialog.show(
            host.activity.supportFragmentManager,
            SAVE_INFORMATION_DIALOG_TAG
        )
    }

    override fun showToolChangeToast(offset: Int, idRes: Int) {
        // Tool and filter names are already visible in the UI; no extra Toast is needed.
    }

    override fun broadcastAddPictureToGallery(uri: Uri) {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            host.activity.contentResolver.notifyChange(uri, null)
            return
        }

        val path = uri.path
        if (path != null) {
            MediaScannerConnection.scanFile(host.activity, arrayOf(path), null, null)
        } else {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = uri
            host.activity.sendBroadcast(mediaScanIntent)
        }
    }

    override fun restoreFragmentListeners() {
        var fragment = findFragmentByTag(COLOR_PICKER_DIALOG_TAG)
        if (fragment != null) {
            setupColorPickerDialogListeners(fragment as ColorPickerDialog)
        }
    }

    override fun setAntialiasingOnToolPaint() {
        host.toolPaint.setAntialiasing()
    }

    override fun setMaskFilterToNull() {
        host.toolPaint.paint.maskFilter = null
        host.toolPaint.previewPaint.maskFilter = null
        toolReference.tool?.let {
            host.toolPaint.paint.alpha = it.drawPaint.alpha
            host.toolPaint.previewPaint.alpha = it.drawPaint.alpha
        }
    }

}
