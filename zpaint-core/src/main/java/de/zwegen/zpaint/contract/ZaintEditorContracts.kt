package de.zwegen.zpaint.contract

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.DisplayMetrics
import android.view.Menu
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.common.ZaintActivityCodes.ActivityRequestCode
import de.zwegen.zpaint.dialog.ZaintStoragePermissionDialog.StoragePermissionRequest
import de.zwegen.zpaint.iotasks.CreateFile.CreateFileCallback
import de.zwegen.zpaint.iotasks.BitmapSize
import de.zwegen.zpaint.iotasks.ZaintDocumentLoadTask.LoadImageCallback
import de.zwegen.zpaint.iotasks.ZaintDocumentSaveTask.SaveImageCallback
import de.zwegen.zpaint.iotasks.WorkspaceReturnValue
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.ui.ZaintLayerListAdapter
import java.io.File

/**
 * Collaboration boundary for the Zaint editor screen.
 *
 * It keeps Android navigation, view operations, editor state, persistence,
 * and presentation separate while preserving the active Zaint workflows.
 */
interface ZaintEditorContracts {
    interface Navigator {
        val isSdkAboveOrEqualM: Boolean
        val isSdkAboveOrEqualQ: Boolean
        val isSdkAboveOrEqualT: Boolean

        fun showColorPickerDialog()

        fun startLoadImageActivity(@ActivityRequestCode requestCode: Int)

        fun startImportImageActivity(@ActivityRequestCode requestCode: Int)

        fun startCreateSaveDocumentActivity(@ActivityRequestCode requestCode: Int, filename: String, mimeType: String)

        fun showOverwriteDialog(permissionCode: Int, isExport: Boolean)

        fun showPngInformationDialog()

        fun showJpgInformationDialog()

        fun startShareImageActivity(bitmap: Bitmap?)

        fun showIndeterminateProgressDialog()

        fun dismissIndeterminateProgressDialog()

        fun showToast(msg: String, duration: Int)

        fun showToast(@StringRes resId: Int, duration: Int)

        fun showSaveErrorDialog()

        fun showLoadErrorDialog()

        fun showRequestPermissionRationaleDialog(
            permissionType: StoragePermissionRequest,
            permissions: Array<String>,
            requestCode: Int
        )

        fun showRequestPermanentlyDeniedPermissionRationaleDialog()

        fun askForPermission(permissions: Array<String>, requestCode: Int)

        fun doIHavePermission(permission: String): Boolean

        fun isPermissionPermanentlyDenied(permissions: Array<String>): Boolean

        fun finishActivity()

        fun showSaveBeforeFinishDialog()

        fun showSaveBeforeNewImageDialog()

        fun showSaveBeforeLoadImageDialog()

        fun showSaveImageInformationDialogWhenStandalone(
            permissionCode: Int,
            imageNumber: Int,
            isExport: Boolean
        )

        fun restoreFragmentListeners()

        fun showToolChangeToast(offset: Int, idRes: Int)

        fun broadcastAddPictureToGallery(uri: Uri)

        fun setAntialiasingOnToolPaint()

        fun showScaleImageRequestDialog(uri: Uri?, requestCode: Int)

        fun setMaskFilterToNull()
    }

    interface MainView {
        val finishing: Boolean
        val isKeyboardShown: Boolean
        val myContentResolver: ContentResolver
        val displayMetrics: DisplayMetrics
        val visibleDrawingSurfaceSize: BitmapSize?
        val presenter: Presenter

        fun initializeActionBar()

        fun superHandleActivityResult(requestCode: Int, resultCode: Int, data: Intent?)

        fun superHandleRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        )

        fun getUriFromFile(file: File): Uri

        fun hideKeyboard()

        fun refreshDrawingSurface()

        fun showContentLoadingProgressBar()

        fun hideContentLoadingProgressBar()
    }

    interface Presenter {
        val imageNumber: Int
        val bitmap: Bitmap?
        val context: Context

        fun initializeFromCleanState()

        fun restoreState(
            isSaved: Boolean,
            savedPictureUri: Uri?,
            cameraImageUri: Uri?
        )

        fun finishInitialize()

        fun removeMoreOptionsItems(menu: Menu?)

        fun replaceImageClicked()

        fun addImageToCurrentLayerClicked()

        fun loadNewImage()

        fun newImageClicked()

        fun discardImageClicked()

        fun saveCopyClicked(isExport: Boolean)

        fun saveImageClicked()

        fun saveAsClicked()

        fun selectSaveFolderClicked()

        fun selectSaveFolderClicked(permissionCode: Int, isExport: Boolean)

        fun saveImageToLocationClicked(permissionCode: Int, isExport: Boolean)

        fun shareImageClicked()

        fun exitClicked()

        fun showOverwriteDialog(permissionCode: Int, isExport: Boolean)

        fun showSaveErrorDialog()

        fun showPngInformationDialog()

        fun showJpgInformationDialog()

        fun onNewImage()

        fun switchBetweenVersions(requestCode: Int, isExport: Boolean)

        fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?)

        fun handleRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        )

        fun onBackPressed()

        fun saveImageConfirmClicked(requestCode: Int, uri: Uri?)

        fun saveCopyConfirmClicked(requestCode: Int, uri: Uri?)

        fun undoClicked()

        fun redoClicked()

        fun showColorPickerClicked()

        fun showLayerMenuClicked()

        fun onCommandPostExecute()

        fun setBottomNavigationColor(color: Int)

        fun onCreateTool()

        fun toolClicked(toolType: ZaintToolKind)

        fun brushPresetClicked(preset: BrushPreset)

        fun saveBeforeLoadImage()

        fun saveBeforeNewImage()

        fun saveBeforeFinish()

        fun finishActivity()

        fun actionToolsClicked()

        fun actionFiltersClicked()

        fun actionCurrentToolClicked()

        fun importFromGalleryClicked()

        fun showImportDialog()

        fun selectFillImageClicked()

        fun bitmapLoadedFromSource(loadedImage: Bitmap)

        fun setLayerAdapter(layerAdapter: ZaintLayerListAdapter)

        fun loadScaledImage(uri: Uri?, @ActivityRequestCode requestCode: Int)

        fun setAntialiasingOnOkClicked()

        fun saveNewTemporaryImage()

        fun deleteTemporaryImage()

        fun openTemporaryFile(): WorkspaceReturnValue?

        fun checkForTemporaryFile(): Boolean

        fun setColorHistoryAfterLoadImage(colorHistory: ColorHistory?)

        fun resetPerspectiveAfterNextCommand()
    }

    interface Model {
        var cameraImageUri: Uri?
        var savedPictureUri: Uri?
        var isSaved: Boolean
        var colorHistory: ColorHistory

        fun wasInitialAnimationPlayed(): Boolean

        fun setInitialAnimationPlayed(wasInitialAnimationPlayed: Boolean)
    }

    interface Interactor {
        fun saveCopy(
            callback: SaveImageCallback,
            requestCode: Int,
            layerModel: ZaintLayerContracts.Model,
            commandSerializer: ZaintProjectSerializer,
            uri: Uri?,
            context: Context
        )

        fun saveCopy(
            callback: SaveImageCallback,
            requestCode: Int,
            layerModel: ZaintLayerContracts.Model,
            uri: Uri?,
            context: Context
        )

        fun createFile(callback: CreateFileCallback, requestCode: Int, filename: String)

        fun saveImage(
            callback: SaveImageCallback,
            requestCode: Int,
            layerModel: ZaintLayerContracts.Model,
            commandSerializer: ZaintProjectSerializer,
            uri: Uri?,
            context: Context
        )

        fun saveImage(
            callback: SaveImageCallback,
            requestCode: Int,
            layerModel: ZaintLayerContracts.Model,
            uri: Uri?,
            context: Context
        )

        fun loadFile(
            callback: LoadImageCallback,
            requestCode: Int,
            uri: Uri?,
            context: Context,
            scaling: Boolean,
            commandSerializer: ZaintProjectSerializer,
            visibleDrawingSurfaceSize: BitmapSize? = null
        )
    }

    interface TopBarViewHolder {
        val height: Int

        fun enableUndoButton()

        fun disableUndoButton()

        fun enableRedoButton()

        fun disableRedoButton()

        fun hide()

        fun show()

    }

    interface DrawerLayoutViewHolder {
        fun closeDrawer(gravity: Int, animate: Boolean)

        fun isDrawerOpen(gravity: Int): Boolean

        fun isDrawerVisible(gravity: Int): Boolean

        fun openDrawer(gravity: Int)
    }

    interface BottomBarViewHolder {
        val isVisible: Boolean

        fun show()

        fun hide()
    }

    interface BottomNavigationViewHolder {
        fun show()

        fun hide()

        fun showCurrentTool(toolType: ZaintToolKind?)

        fun enableColorItemView(show: Boolean)

        fun setColorButtonColor(@ColorInt color: Int)
    }

    interface BottomNavigationAppearance {
        fun showCurrentTool(toolType: ZaintToolKind)
    }
}
