package de.zwegen.zpaint.presenter

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import android.os.CountDownTimer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.ZaintSettings
import de.zwegen.zpaint.colorpicker.ColorHistory
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.common.CREATE_FILE_DEFAULT
import de.zwegen.zpaint.common.DEFAULT_CANVAS_HEIGHT
import de.zwegen.zpaint.common.DEFAULT_CANVAS_WIDTH
import de.zwegen.zpaint.common.LOAD_IMAGE_DEFAULT
import de.zwegen.zpaint.common.LOAD_IMAGE_FILL
import de.zwegen.zpaint.common.LOAD_IMAGE_IMPORT_PNG
import de.zwegen.zpaint.common.ZaintActivityCodes.ActivityRequestCode
import de.zwegen.zpaint.common.ZaintActivityCodes.CreateFileRequestCode
import de.zwegen.zpaint.common.ZaintActivityCodes.LoadImageRequestCode
import de.zwegen.zpaint.common.ZaintActivityCodes.PermissionRequestCode
import de.zwegen.zpaint.common.ZaintActivityCodes.SaveImageRequestCode
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_COPY
import de.zwegen.zpaint.common.PERMISSION_REQUEST_CODE_IMPORT_PICTURE
import de.zwegen.zpaint.common.PERMISSION_REQUEST_CODE_REPLACE_PICTURE
import de.zwegen.zpaint.common.REQUEST_CODE_CREATE_SAVE_FILE
import de.zwegen.zpaint.common.REQUEST_CODE_FILL_IMAGE
import de.zwegen.zpaint.common.REQUEST_CODE_IMPORT_PNG
import de.zwegen.zpaint.common.REQUEST_CODE_LOAD_PICTURE
import de.zwegen.zpaint.common.SAVE_IMAGE_DEFAULT
import de.zwegen.zpaint.common.SAVE_IMAGE_FINISH
import de.zwegen.zpaint.common.SAVE_IMAGE_LOAD_NEW
import de.zwegen.zpaint.common.SAVE_IMAGE_NEW_EMPTY
import de.zwegen.zpaint.common.TEMP_PICTURE_NAME
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.contract.ZaintEditorContracts.Interactor
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView
import de.zwegen.zpaint.controller.ZaintToolControl
import de.zwegen.zpaint.dialog.ZaintStoragePermissionDialog
import de.zwegen.zpaint.iotasks.BitmapReturnValue
import de.zwegen.zpaint.iotasks.CreateFile.CreateFileCallback
import de.zwegen.zpaint.iotasks.ZaintDocumentLoadTask.LoadImageCallback
import de.zwegen.zpaint.iotasks.ZaintDocumentSaveTask.SaveImageCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.iotasks.WorkspaceReturnValue
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.implementation.ZaintBrushTool
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.CLICK_TIMEOUT_MILLIS
import de.zwegen.zpaint.tools.implementation.CONSTANT_3
import de.zwegen.zpaint.tools.implementation.BorderTool
import de.zwegen.zpaint.tools.implementation.ZaintLineTool
import de.zwegen.zpaint.tools.implementation.ZaintDrawingPaint
import de.zwegen.zpaint.tools.implementation.FilterTool
import de.zwegen.zpaint.tools.implementation.PerspectiveTool
import de.zwegen.zpaint.tools.implementation.RotateTool
import de.zwegen.zpaint.ui.ZaintLayerListAdapter
import java.io.File
import java.util.Locale

@SuppressWarnings("LongParameterList", "LargeClass", "ThrowingExceptionsWithoutMessageOrCause")
/**
 * Coordinates the Zaint editor workflow between its screen contracts,
 * document persistence, history, tools, and layer UI.
 */
open class ZaintEditorPresenter(
    override val fileActivity: Activity?,
    private val view: MainView,
    private val model: ZaintEditorContracts.Model,
    private val workspace: Workspace,
    private val navigator: ZaintEditorContracts.Navigator,
    private val interactor: Interactor,
    private val topBarViewHolder: ZaintEditorContracts.TopBarViewHolder,
    private val bottomBarViewHolder: ZaintEditorContracts.BottomBarViewHolder,
    private val filterBarViewHolder: ZaintEditorContracts.BottomBarViewHolder,
    private val drawerLayoutViewHolder: ZaintEditorContracts.DrawerLayoutViewHolder,
    private val bottomNavigationViewHolder: ZaintEditorContracts.BottomNavigationViewHolder,
    private val commandFactory: ZaintCommandFactoryApi,
    private val commandManager: ZaintCommandTimeline,
    private val toolController: ZaintToolControl,
    private val sharedPreferences: ZaintSettings,
    private val idlingResource: CountingIdlingResource,
    override val context: Context,
    private val internalMemoryPath: File,
    private val commandSerializer: ZaintProjectSerializer
) : ZaintEditorContracts.Presenter, SaveImageCallback, LoadImageCallback, CreateFileCallback {
    private var downTimer: CountDownTimer? = null
    private var layerAdapter: ZaintLayerListAdapter? = null
    private var resetPerspectiveAfterNextCommand = false
    private var fitPerspectiveToWidthAfterNextCommand = false
    private var isExport = false
    private var pendingCreateDocumentPermission = PERMISSION_EXTERNAL_STORAGE_SAVE
    private var wasImageLoaded = false
    private val isImageUnchanged: Boolean
        get() = !commandManager.isUndoAvailable

    override val bitmap: Bitmap?
        get() = workspace.bitmapOfAllLayers

    override val isFinishing: Boolean
        get() = view.finishing

    override val contentResolver: ContentResolver
        get() = view.myContentResolver

    override val imageNumber: Int
        get() {
            val imageNumber = sharedPreferences.nextImageNumber
            if (imageNumber == 0) {
                countUpImageNumber()
            }
            return sharedPreferences.nextImageNumber
        }

    override fun replaceImageClicked() {
        switchBetweenVersions(PERMISSION_REQUEST_CODE_REPLACE_PICTURE, false)
        setFirstCheckBoxInLayerMenu()
    }

    override fun addImageToCurrentLayerClicked() {
        setTool(ZaintToolKind.IMPORTPNG)
        switchBetweenVersions(PERMISSION_REQUEST_CODE_IMPORT_PICTURE)
    }

    private fun setFirstCheckBoxInLayerMenu() {
        layerAdapter?.getViewHolderAt(0)?.apply { setLayerVisibilityCheckbox(true) }
    }

    override fun saveBeforeLoadImage() {
        navigator.showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW,
            imageNumber,
            false
        )
    }

    override fun loadNewImage() {
        navigator.startLoadImageActivity(REQUEST_CODE_LOAD_PICTURE)
        setFirstCheckBoxInLayerMenu()
    }

    override fun newImageClicked() {
        if (isImageUnchanged || model.isSaved) {
            onNewImage()
            setFirstCheckBoxInLayerMenu()
        } else {
            navigator.showSaveBeforeNewImageDialog()
            setFirstCheckBoxInLayerMenu()
        }
    }

    override fun saveBeforeNewImage() {
        navigator.showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY,
            imageNumber,
            false
        )
    }

    private fun showSecurityQuestionBeforeExit() {
        if (isImageUnchanged || model.isSaved) {
            finishActivity()
        } else {
            navigator.showSaveBeforeFinishDialog()
        }
    }

    override fun finishActivity() {
        navigator.finishActivity()
    }

    override fun saveBeforeFinish() {
        navigator.showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH,
            imageNumber,
            false
        )
    }

    override fun saveCopyClicked(isExport: Boolean) {
        navigator.showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
            imageNumber,
            isExport
        )
    }

    override fun saveImageClicked() {
        navigator.showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            imageNumber,
            false
        )
    }

    private fun persistUriPermission(uri: Uri, resultData: Intent?) {
        val grantedFlags = (resultData?.flags ?: 0) and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (grantedFlags == 0) {
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, grantedFlags)
        } catch (_: SecurityException) {
            // Providers that do not offer persistent grants use Save As instead of overwriting.
        }
    }

    private fun sourceFolderPathFor(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") {
            return null
        }
        val documentId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!documentId.startsWith("primary:")) {
            return null
        }
        val documentPath = documentId.substringAfter(':', "")
        return documentPath.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
    }

    private fun sourceFolderNameFor(uri: Uri): String? =
        sourceFolderPathFor(uri)?.substringAfterLast('/')

    override fun saveAsClicked() {
        navigator.showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            imageNumber,
            false
        )
    }

    override fun selectSaveFolderClicked() {
        selectSaveFolderClicked(PERMISSION_EXTERNAL_STORAGE_SAVE, false)
    }

    override fun selectSaveFolderClicked(permissionCode: Int, isExport: Boolean) {
        saveImageToLocationClicked(permissionCode, isExport)
    }

    override fun shareImageClicked() {
        view.refreshDrawingSurface()
        val bitmap: Bitmap? = workspace.bitmapOfAllLayers
        navigator.startShareImageActivity(bitmap)
    }

    private fun countUpImageNumber() {
        var imageNumber = sharedPreferences.nextImageNumber
        imageNumber++
        sharedPreferences.nextImageNumber = imageNumber
    }

    override fun exitClicked() {
        showSecurityQuestionBeforeExit()
    }

    override fun showOverwriteDialog(permissionCode: Int, isExport: Boolean) {
        navigator.showOverwriteDialog(permissionCode, isExport)
    }

    override fun showSaveErrorDialog() {
        navigator.showSaveErrorDialog()
    }

    override fun showPngInformationDialog() {
        navigator.showPngInformationDialog()
    }

    override fun showJpgInformationDialog() {
        navigator.showJpgInformationDialog()
    }

    override fun onNewImage() {
        resetPerspectiveAfterNextCommand = true
        model.savedPictureUri = null
        ZaintDocumentStorage.sourceFolderName = null
        ZaintDocumentStorage.sourceRelativePath = null
        ZaintDocumentStorage.filename = "image"
        ZaintDocumentStorage.compressFormat = Bitmap.CompressFormat.PNG
        ZaintDocumentStorage.fileType = ZaintDocumentStorage.FileType.PNG
        ZaintDocumentStorage.deleteTempFile(internalMemoryPath)
        val initCommand = commandFactory.createInitCommand(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)
        commandManager.setInitialStateCommand(initCommand)
        commandManager.reset()
    }

    override fun discardImageClicked() {
        commandManager.addCommand(commandFactory.createResetCommand())
    }

    fun switchBetweenVersions(@PermissionRequestCode requestCode: Int) {
        switchBetweenVersions(requestCode, false)
    }

    override fun switchBetweenVersions(@PermissionRequestCode requestCode: Int, isExport: Boolean) {
        this.isExport = isExport

        if (navigator.isSdkAboveOrEqualM) {
            askForReadAndWriteExternalStoragePermission(requestCode)
            when (requestCode) {
                PERMISSION_REQUEST_CODE_REPLACE_PICTURE,
                PERMISSION_REQUEST_CODE_IMPORT_PICTURE -> Unit
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW,
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY,
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH,
                PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
                PERMISSION_EXTERNAL_STORAGE_SAVE -> checkForDefaultFilename()
            }
        } else {
            if (requestCode == PERMISSION_REQUEST_CODE_REPLACE_PICTURE) {
                if (isImageUnchanged || model.isSaved) {
                    navigator.startLoadImageActivity(REQUEST_CODE_LOAD_PICTURE)
                    setFirstCheckBoxInLayerMenu()
                } else {
                    navigator.showSaveBeforeLoadImageDialog()
                    setFirstCheckBoxInLayerMenu()
                }
            } else {
                askForReadAndWriteExternalStoragePermission(requestCode)
            }
        }
    }

    private fun askForReadAndWriteExternalStoragePermission(@PermissionRequestCode requestCode: Int) {
        @TargetApi(Build.VERSION_CODES.TIRAMISU)
        if (navigator.isSdkAboveOrEqualT) {
            val permissions = imageReadPermissions()
            if (!hasImageReadPermission()) {
                navigator.askForPermission(
                    permissions,
                    requestCode
                )
            } else {
                handleRequestPermissionsResult(
                    requestCode,
                    permissions,
                    IntArray(permissions.size) { PackageManager.PERMISSION_GRANTED }
                )
            }
        } else if (navigator.isSdkAboveOrEqualQ) {
            if (!navigator.doIHavePermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                navigator.askForPermission(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    requestCode
                )
            } else {
                handleRequestPermissionsResult(
                    requestCode,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    intArrayOf(PackageManager.PERMISSION_GRANTED)
                )
            }
        } else {
            if (navigator.isSdkAboveOrEqualM && !navigator.doIHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                navigator.askForPermission(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    requestCode
                )
            } else {
                handleRequestPermissionsResult(
                    requestCode,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    intArrayOf(PackageManager.PERMISSION_GRANTED)
                )
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun imageReadPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasImageReadPermission(): Boolean =
        navigator.doIHavePermission(Manifest.permission.READ_MEDIA_IMAGES) ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            navigator.doIHavePermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun isStoragePermission(permission: String): Boolean =
        permission == Manifest.permission.READ_EXTERNAL_STORAGE ||
            permission == Manifest.permission.WRITE_EXTERNAL_STORAGE ||
            permission == Manifest.permission.READ_MEDIA_IMAGES ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            permission == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED

    private fun hasGrantedStoragePermission(
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean =
        permissions.indices.any { index ->
            isStoragePermission(permissions[index]) &&
                grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        }

    private fun checkForDefaultFilename() {
        val standard = "image$imageNumber"
        if (ZaintDocumentStorage.filename == standard) {
            countUpImageNumber()
        }
    }

    override fun handleActivityResult(
        @ActivityRequestCode requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        val imageUri = data?.data
        when (requestCode) {
            REQUEST_CODE_IMPORT_PNG -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                setTool(ZaintToolKind.IMPORTPNG)
                toolController.switchTool(ZaintToolKind.IMPORTPNG)
                interactor.loadFile(
                    this,
                    LOAD_IMAGE_IMPORT_PNG,
                    imageUri,
                    context,
                    false,
                    commandSerializer,
                    view.visibleDrawingSurfaceSize
                )
            }
            REQUEST_CODE_FILL_IMAGE -> {
                if (resultCode != Activity.RESULT_OK) return
                interactor.loadFile(
                    this,
                    LOAD_IMAGE_FILL,
                    imageUri,
                    context,
                    false,
                    commandSerializer,
                    view.visibleDrawingSurfaceSize
                )
            }
            REQUEST_CODE_LOAD_PICTURE -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                imageUri?.let {
                    persistUriPermission(it, data)
                    ZaintDocumentStorage.sourceFolderName = sourceFolderNameFor(it)
                    ZaintDocumentStorage.sourceRelativePath = sourceFolderPathFor(it)
                }
                interactor.loadFile(
                    this,
                    LOAD_IMAGE_DEFAULT,
                    imageUri,
                    context,
                    false,
                    commandSerializer,
                    view.visibleDrawingSurfaceSize
                )
            }
            REQUEST_CODE_CREATE_SAVE_FILE -> {
                if (resultCode != Activity.RESULT_OK || imageUri == null) {
                    return
                }
                persistUriPermission(imageUri, data)
                ZaintDocumentStorage.storeImageUri = imageUri
                if (pendingCreateDocumentPermission == PERMISSION_EXTERNAL_STORAGE_SAVE_COPY) {
                    saveCopyConfirmClicked(SAVE_IMAGE_DEFAULT, imageUri)
                } else {
                    saveImageConfirmClicked(
                        saveRequestCodeForPermission(pendingCreateDocumentPermission),
                        imageUri
                    )
                }
                checkForDefaultFilename()
            }
            else -> view.superHandleActivityResult(requestCode, resultCode, data)
        }
    }

    override fun saveImageToLocationClicked(permissionCode: Int, isExport: Boolean) {
        pendingCreateDocumentPermission = permissionCode
        this.isExport = isExport
        navigator.startCreateSaveDocumentActivity(
            REQUEST_CODE_CREATE_SAVE_FILE,
            ZaintDocumentStorage.defaultFileName,
            ZaintDocumentStorage.currentMimeType
        )
    }

    private fun saveRequestCodeForPermission(@PermissionRequestCode permissionCode: Int): Int =
        when (permissionCode) {
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW -> SAVE_IMAGE_LOAD_NEW
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY -> SAVE_IMAGE_NEW_EMPTY
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH -> SAVE_IMAGE_FINISH
            else -> SAVE_IMAGE_DEFAULT
        }

    override fun handleRequestPermissionsResult(
        @PermissionRequestCode requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (permissions.any { isStoragePermission(it) }) {
            if (hasGrantedStoragePermission(permissions, grantResults)) {
                when (requestCode) {
                    PERMISSION_EXTERNAL_STORAGE_SAVE -> {
                        saveImageConfirmClicked(
                            SAVE_IMAGE_DEFAULT,
                            ZaintDocumentStorage.storeImageUri
                        )
                        checkForDefaultFilename()
                    }
                    PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH -> {
                        saveImageConfirmClicked(
                            SAVE_IMAGE_FINISH,
                            ZaintDocumentStorage.storeImageUri
                        )
                        checkForDefaultFilename()
                    }
                    PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW -> {
                        saveImageConfirmClicked(
                            SAVE_IMAGE_LOAD_NEW,
                            ZaintDocumentStorage.storeImageUri
                        )
                        checkForDefaultFilename()
                    }
                    PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY -> {
                        saveImageConfirmClicked(
                            SAVE_IMAGE_NEW_EMPTY,
                            ZaintDocumentStorage.storeImageUri
                        )
                        checkForDefaultFilename()
                    }
                    PERMISSION_EXTERNAL_STORAGE_SAVE_COPY -> {
                        saveCopyConfirmClicked(
                            SAVE_IMAGE_DEFAULT,
                            ZaintDocumentStorage.storeImageUri
                        )
                        checkForDefaultFilename()
                    }
                    PERMISSION_REQUEST_CODE_REPLACE_PICTURE ->
                        if (isImageUnchanged || model.isSaved) {
                            navigator.startLoadImageActivity(REQUEST_CODE_LOAD_PICTURE)
                        } else {
                            navigator.showSaveBeforeLoadImageDialog()
                        }
                    PERMISSION_REQUEST_CODE_IMPORT_PICTURE -> navigator.startImportImageActivity(
                        REQUEST_CODE_IMPORT_PNG
                    )
                    else -> view.superHandleRequestPermissionsResult(
                        requestCode,
                        permissions,
                        grantResults
                    )
                }
            } else {
                if (navigator.isPermissionPermanentlyDenied(permissions)) {
                    navigator.showRequestPermanentlyDeniedPermissionRationaleDialog()
                } else {
                    navigator.showRequestPermissionRationaleDialog(
                        ZaintStoragePermissionDialog.StoragePermissionRequest.EXTERNAL_STORAGE,
                        permissions, requestCode
                    )
                }
            }
        } else {
            view.superHandleRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onBackPressed() {
        if (drawerLayoutViewHolder.isDrawerOpen(GravityCompat.START)) {
            drawerLayoutViewHolder.closeDrawer(Gravity.START, true)
        } else if (drawerLayoutViewHolder.isDrawerOpen(GravityCompat.END)) {
            drawerLayoutViewHolder.closeDrawer(Gravity.END, true)
        } else if (!toolController.isDefaultTool) {
            switchTool(ZaintToolKind.BRUSH)
        } else {
            showSecurityQuestionBeforeExit()
        }
    }

    override fun saveImageConfirmClicked(requestCode: Int, uri: Uri?) {
        view.refreshDrawingSurface()
        interactor.saveImage(this, requestCode, workspace.layerModel, commandSerializer, uri, context)
    }

    override fun saveCopyConfirmClicked(requestCode: Int, uri: Uri?) {
        view.refreshDrawingSurface()
        interactor.saveCopy(this, requestCode, workspace.layerModel, commandSerializer, uri, context)
    }

    override fun undoClicked() {
        idlingResource.increment()
        if (view.isKeyboardShown) {
            view.hideKeyboard()
        } else {
            if (toolController.currentTool is ZaintLineTool) {
                (toolController.currentTool as ZaintLineTool).undoChangePaintColor(Color.BLACK, false)
            } else {
                commandManager.undo()
            }
        }
        idlingResource.decrement()
    }

    override fun redoClicked() {
        idlingResource.increment()
        if (view.isKeyboardShown) {
            view.hideKeyboard()
        } else {
            if (toolController.currentTool is ZaintLineTool) {
                (toolController.currentTool as ZaintLineTool).redoLineTool()
            } else {
                commandManager.redo()
            }
        }
        idlingResource.decrement()
    }

    override fun showColorPickerClicked() {
        commitPendingFilterIfNeeded()
        bottomBarViewHolder.hide()
        filterBarViewHolder.hide()
        navigator.showColorPickerDialog()
    }

    override fun showLayerMenuClicked() {
        idlingResource.increment()
        commitPendingFilterIfNeeded()
        bottomBarViewHolder.hide()
        filterBarViewHolder.hide()
        layerAdapter?.apply {
            for (position in 0 until itemCount) {
                val currentHolder = getViewHolderAt(position)
                currentHolder?.let {
                    val layer = presenter.getLayerItem(position)
                    if (it.bitmap != null && layer != null) {
                        it.updateImageView(layer)
                    }
                }
            }
        }
        drawerLayoutViewHolder.openDrawer(Gravity.END)
        idlingResource.decrement()
    }

    override fun onCommandPostExecute() {
        navigator.dismissIndeterminateProgressDialog()
        if (resetPerspectiveAfterNextCommand) {
            resetPerspectiveAfterNextCommand = false
            workspace.resetPerspective()
            if (fitPerspectiveToWidthAfterNextCommand) {
                fitPerspectiveToWidthAfterNextCommand = false
                workspace.perspective.resetScaleAndTranslationToFitWidth()
            }
        }
        model.isSaved = false
        toolController.resetToolInternalState()
        view.refreshDrawingSurface()
        refreshTopBarButtons()
    }

    override fun setBottomNavigationColor(color: Int) {
        bottomNavigationViewHolder.setColorButtonColor(color)
    }

    override fun initializeFromCleanState() {
        wasImageLoaded = false
        toolController.resetToolInternalStateOnImageLoaded()
        model.savedPictureUri = null
    }

    override fun finishInitialize() {
        refreshTopBarButtons()
        toolController.toolColor?.let { bottomNavigationViewHolder.setColorButtonColor(it) }
        bottomNavigationViewHolder.showCurrentTool(toolController.toolType)
        view.initializeActionBar()
        if (commandManager.isBusy) {
            navigator.showIndeterminateProgressDialog()
        }
    }

    override fun removeMoreOptionsItems(menu: Menu?) {
        Unit
    }

    override fun restoreState(
        isSaved: Boolean,
        savedPictureUri: Uri?,
        cameraImageUri: Uri?
    ) {
        model.isSaved = isSaved
        model.savedPictureUri = savedPictureUri
        model.cameraImageUri = cameraImageUri
        navigator.restoreFragmentListeners()
        toolController.resetToolInternalStateOnImageLoaded()
    }

    override fun onCreateTool() {
        toolController.createTool()
    }

    private fun refreshTopBarButtons() {
        if (commandManager.isUndoAvailable) {
            topBarViewHolder.enableUndoButton()
        } else {
            topBarViewHolder.disableUndoButton()
        }
        if (commandManager.isRedoAvailable) {
            topBarViewHolder.enableRedoButton()
        } else {
            topBarViewHolder.disableRedoButton()
        }
    }

    override fun toolClicked(toolType: ZaintToolKind) {
        idlingResource.increment()
        bottomBarViewHolder.hide()
        filterBarViewHolder.hide()
        if (toolController.toolType === toolType && toolController.hasToolOptionsView()) {
            toolController.toggleToolOptionsView()
        } else {
            checkForImplicitToolApplication()
            switchTool(toolType)
        }
        idlingResource.decrement()
    }

    override fun brushPresetClicked(preset: BrushPreset) {
        idlingResource.increment()
        bottomBarViewHolder.hide()
        filterBarViewHolder.hide()
        checkForImplicitToolApplication()
        setTool(ZaintToolKind.BRUSH)
        toolController.switchTool(ZaintToolKind.BRUSH)
        (toolController.currentTool as? ZaintBrushTool)?.changeBrushPreset(preset)
        idlingResource.decrement()
    }

    private fun checkForImplicitToolApplication() {
        commitPendingFilterIfNeeded()
    }

    private fun switchTool(type: ZaintToolKind) {
        navigator.setMaskFilterToNull()
        view.hideKeyboard()
        if (type == ZaintToolKind.BORDER) {
            layerAdapter?.presenter?.selectBottomLayer()
        }
        downTimer = object :
            CountDownTimer(
                if (toolController.toolList.contains(toolController.currentTool?.toolType)) CLICK_TIMEOUT_MILLIS else 0L,
                CLICK_TIMEOUT_MILLIS / CONSTANT_3
            ) {
            override fun onTick(millisUntilFinished: Long) {
                workspace.invalidate()
            }
            override fun onFinish() {
                downTimer?.cancel()
                workspace.invalidate()
                setTool(type)
                toolController.switchTool(type)
                if (type === ZaintToolKind.IMPORTPNG) {
                    importFromGalleryClicked()
                }
            }
        }.start()
    }

    private fun setTool(toolType: ZaintToolKind) {
        idlingResource.increment()
        bottomBarViewHolder.hide()
        filterBarViewHolder.hide()
        bottomNavigationViewHolder.showCurrentTool(toolType)
        val offset = topBarViewHolder.height
        navigator.showToolChangeToast(offset, toolType.nameResource)
        idlingResource.decrement()
    }

    override fun onCreateFilePostExecute(@CreateFileRequestCode requestCode: Int, file: File?) {
        if (file == null) {
            navigator.showSaveErrorDialog()
            return
        }
        if (requestCode == CREATE_FILE_DEFAULT) {
            model.savedPictureUri = view.getUriFromFile(file)
        } else {
            throw IllegalArgumentException()
        }
    }

    override fun loadScaledImage(uri: Uri?, @LoadImageRequestCode requestCode: Int) {
        when (requestCode) {
            LOAD_IMAGE_IMPORT_PNG -> {
                setTool(ZaintToolKind.IMPORTPNG)
                toolController.switchTool(ZaintToolKind.IMPORTPNG)
                interactor.loadFile(
                    this,
                    LOAD_IMAGE_IMPORT_PNG,
                    uri,
                    context,
                    true,
                    commandSerializer,
                    view.visibleDrawingSurfaceSize
                )
            }
            LOAD_IMAGE_DEFAULT -> interactor.loadFile(
                this,
                LOAD_IMAGE_DEFAULT,
                uri,
                context,
                true,
                commandSerializer,
                view.visibleDrawingSurfaceSize
            )
            LOAD_IMAGE_FILL -> interactor.loadFile(
                this,
                LOAD_IMAGE_FILL,
                uri,
                context,
                true,
                commandSerializer,
                view.visibleDrawingSurfaceSize
            )
            else -> Log.e(ZaintEditorActivity.TAG, "wrong request code for loading pictures")
        }
    }

    override fun onLoadImagePostExecute(
        @LoadImageRequestCode requestCode: Int,
        uri: Uri?,
        result: BitmapReturnValue?
    ) {
        if (result == null) {
            navigator.showLoadErrorDialog()
            return
        }
        if (result.model != null) {
            commandManager.loadProjectHistory(result.model)
            resetPerspectiveAfterNextCommand = true
            setColorHistoryAfterLoadImage(result.colorHistory)
            model.savedPictureUri = uri
            model.cameraImageUri = null
            wasImageLoaded = true
            if (uri != null) {
                val name = getFileName(uri)
                if (name != null) {
                    ZaintDocumentStorage.filename = name.substring(0, name.length - ZaintDocumentStorage.FileType.ZAINT.toExtension().length)
                }
            }
            ZaintDocumentStorage.fileType = ZaintDocumentStorage.FileType.ZAINT
            ZaintDocumentStorage.compressFormat = Bitmap.CompressFormat.PNG
            return
        }
        if (result.toBeScaled) {
            navigator.showScaleImageRequestDialog(uri, requestCode)
            return
        }
        when (requestCode) {
            LOAD_IMAGE_DEFAULT -> {
                resetPerspectiveAfterNextCommand = true
                fitPerspectiveToWidthAfterNextCommand = true
                if (result.bitmap != null) {
                    result.bitmap?.let {
                        commandManager.setInitialStateCommand(commandFactory.createInitCommand(it))
                    }
                } else {
                    result.layerList?.let {
                        commandManager.setInitialStateCommand(commandFactory.createInitCommand(it))
                    }
                }
                commandManager.reset()
                toolController.resetToolInternalStateOnImageLoaded()
                model.savedPictureUri = uri
                model.cameraImageUri = null
                wasImageLoaded = true
                if (uri != null) {
                    val name = getFileName(uri)
                    if (name != null) {
                        val lowerName = name.lowercase(Locale.US)
                        val fileNameExtensionLength = when {
                            lowerName.endsWith(ZaintDocumentStorage.FileType.JPG.toExtension()) ->
                                ZaintDocumentStorage.FileType.JPG.toExtension().length
                            lowerName.endsWith(".jpeg") -> ".jpeg".length
                            lowerName.endsWith(ZaintDocumentStorage.FileType.PNG.toExtension()) ->
                                ZaintDocumentStorage.FileType.PNG.toExtension().length
                            lowerName.endsWith(".svg") -> ".svg".length
                            else -> ZaintDocumentStorage.fileType.toExtension().length
                        }
                        if (lowerName.endsWith(ZaintDocumentStorage.FileType.JPG.toExtension()) || lowerName.endsWith(".jpeg")) {
                            ZaintDocumentStorage.compressFormat = Bitmap.CompressFormat.JPEG
                            ZaintDocumentStorage.fileType = ZaintDocumentStorage.FileType.JPG
                        } else if (lowerName.endsWith(ZaintDocumentStorage.FileType.PNG.toExtension()) || lowerName.endsWith(".svg")) {
                            ZaintDocumentStorage.compressFormat = Bitmap.CompressFormat.PNG
                            ZaintDocumentStorage.fileType = ZaintDocumentStorage.FileType.PNG
                        } else {
                            ZaintDocumentStorage.compressFormat = Bitmap.CompressFormat.PNG
                            ZaintDocumentStorage.fileType = ZaintDocumentStorage.FileType.PNG
                        }
                        ZaintDocumentStorage.filename = name.substring(0, name.length - fileNameExtensionLength)
                    }
                }
            }
            LOAD_IMAGE_IMPORT_PNG ->
                if (toolController.toolType === ZaintToolKind.IMPORTPNG) {
                    toolController.setBitmapFromSource(result.bitmap)
                } else {
                    Log.e(
                        ZaintEditorActivity.TAG,
                        "importPngToFloatingBox: Current tool is no ImportTool as required"
                    )
                }
            LOAD_IMAGE_FILL ->
                if (toolController.toolType === ZaintToolKind.FILL && result.bitmap != null) {
                    toolController.setBitmapFromSource(result.bitmap)
                } else {
                    Log.e(ZaintEditorActivity.TAG, "fillImage: Current tool is no FillTool as required")
                }
            else -> throw IllegalArgumentException()
        }
    }

    override fun onLoadImagePreExecute(@LoadImageRequestCode requestCode: Int) = Unit

    override fun onSaveImagePreExecute(@SaveImageRequestCode requestCode: Int) {
        view.showContentLoadingProgressBar()
    }

    override fun onSaveImagePostExecute(
        @SaveImageRequestCode requestCode: Int,
        uri: Uri?,
        saveAsCopy: Boolean
    ) {
        view.hideContentLoadingProgressBar()
        if (uri == null) {
            navigator.showSaveErrorDialog()
            return
        }
        if (saveAsCopy) {
            var msg: String? = context.getString(R.string.copy_to)
            fileActivity?.let {
                msg += getPathFromUri(it, uri)
            }
            navigator.showToast(msg ?: "null", Toast.LENGTH_LONG)
        } else {
            var msg: String? = context.getString(R.string.saved_to)
            fileActivity?.let {
                msg += getPathFromUri(it, uri)
            }
            navigator.showToast(msg ?: "null", Toast.LENGTH_LONG)
            model.savedPictureUri = uri
            model.isSaved = true
        }
        navigator.broadcastAddPictureToGallery(uri)
        when (requestCode) {
            SAVE_IMAGE_NEW_EMPTY -> onNewImage()
            SAVE_IMAGE_DEFAULT -> {
            }
            SAVE_IMAGE_FINISH -> {
                navigator.finishActivity()
                return
            }
            SAVE_IMAGE_LOAD_NEW -> navigator.startLoadImageActivity(
                REQUEST_CODE_LOAD_PICTURE
            )
            else -> throw IllegalArgumentException()
        }
    }

    override fun actionToolsClicked() {
        commitPendingFilterIfNeeded()
        if (toolController.toolOptionsViewVisible()) {
            toolController.hideToolOptionsViewImmediately()
        }
        if (filterBarViewHolder.isVisible) {
            filterBarViewHolder.hide()
        }
        if (bottomBarViewHolder.isVisible) {
            bottomBarViewHolder.hide()
        } else {
            val layer = layerAdapter?.presenter?.getLayerItem(workspace.currentLayerIndex)
            if (layer != null && !layer.isVisible) {
                navigator.showToast(R.string.no_tools_on_hidden_layer, Toast.LENGTH_SHORT)
                return
            }
            bottomBarViewHolder.show()
        }
    }

    override fun actionFiltersClicked() {
        commitPendingFilterIfNeeded()
        if (toolController.toolOptionsViewVisible()) {
            toolController.hideToolOptionsView()
        }
        if (bottomBarViewHolder.isVisible) {
            bottomBarViewHolder.hide()
        }
        if (filterBarViewHolder.isVisible) {
            filterBarViewHolder.hide()
        } else {
            val layer = layerAdapter?.presenter?.getLayerItem(workspace.currentLayerIndex)
            if (layer != null && !layer.isVisible) {
                navigator.showToast(R.string.no_tools_on_hidden_layer, Toast.LENGTH_SHORT)
                return
            }
            filterBarViewHolder.show()
        }
    }

    override fun actionCurrentToolClicked() {
        commitPendingFilterIfNeeded()
        if (bottomBarViewHolder.isVisible) {
            bottomBarViewHolder.hide()
        }
        if (filterBarViewHolder.isVisible) {
            filterBarViewHolder.hide()
        }
        if (toolController.hasToolOptionsView()) {
            toolController.toggleToolOptionsView()
        }
    }

    private fun commitPendingFilterIfNeeded() {
        (toolController.currentTool as? FilterTool)?.finishPendingFilterOnNavigation()
        (toolController.currentTool as? RotateTool)?.finishPendingRotationOnNavigation()
        (toolController.currentTool as? PerspectiveTool)?.finishPendingPerspectiveOnNavigation()
        (toolController.currentTool as? BorderTool)?.finishPendingBorderOnNavigation()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = fileActivity?.contentResolver?.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        return result
    }

    override fun setLayerAdapter(layerAdapter: ZaintLayerListAdapter) {
        this.layerAdapter = layerAdapter
    }

    override fun importFromGalleryClicked() {
        switchBetweenVersions(PERMISSION_REQUEST_CODE_IMPORT_PICTURE)
    }

    override fun selectFillImageClicked() {
        navigator.startImportImageActivity(REQUEST_CODE_FILL_IMAGE)
    }

    override fun showImportDialog() = importFromGalleryClicked()

    override fun bitmapLoadedFromSource(loadedImage: Bitmap) {
        toolController.setBitmapFromSource(loadedImage)
    }

    override fun setAntialiasingOnOkClicked() {
        navigator.setAntialiasingOnToolPaint()
    }

    override fun saveNewTemporaryImage() {
        ZaintDocumentStorage.saveTemporaryPictureFile(internalMemoryPath, commandSerializer)
    }

    override fun deleteTemporaryImage() {
        ZaintDocumentStorage.deleteTempFile(internalMemoryPath)
    }

    override fun openTemporaryFile(): WorkspaceReturnValue? =
        ZaintDocumentStorage.openTemporaryPictureFile(internalMemoryPath, commandSerializer)

    override fun checkForTemporaryFile(): Boolean =
        ZaintDocumentStorage.checkForTemporaryFile(internalMemoryPath)

    override fun resetPerspectiveAfterNextCommand() {
        resetPerspectiveAfterNextCommand = true
    }

    override fun setColorHistoryAfterLoadImage(colorHistory: ColorHistory?) {
        var history = colorHistory
        history = history ?: ColorHistory()
        model.colorHistory = history
        var newPaintColor: Int = ZaintDrawingPaint(context).color
        if (history.colors.isNotEmpty()) {
            newPaintColor = history.colors.last()
        }
        toolController.currentTool?.changePaintColor(newPaintColor)
        setBottomNavigationColor(newPaintColor)
    }

    fun hideBottomBarViewHolder() {
        if (bottomBarViewHolder.isVisible) {
            bottomBarViewHolder.hide()
        }
        if (filterBarViewHolder.isVisible) {
            filterBarViewHolder.hide()
        }
    }

    companion object {
        @JvmStatic
        fun getPathFromUri(context: Context, uri: Uri): String {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                if (isExternalStorageDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }
                } else if (isDownloadsDocument(uri)) {
                    val id = DocumentsContract.getDocumentId(uri)
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        id.toLong()
                    )
                    return getDataColumn(context, contentUri, null, null)
                } else if (isMediaDocument(uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":").toTypedArray()
                    val contentUri: Uri? = when (split[0]) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> null
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    return getDataColumn(context, contentUri, selection, selectionArgs)
                }
            } else if ("content".equals(uri.scheme, ignoreCase = true)) {
                return if (isGooglePhotosUri(uri)) {
                    uri.lastPathSegment.toString()
                } else getDataColumn(context, uri, null, null)
            } else if ("file".equals(uri.scheme, ignoreCase = true)) {
                return uri.path.toString()
            }
            return ""
        }

        @SuppressWarnings("SwallowedException")
        private fun getDataColumn(
            context: Context,
            uri: Uri?,
            selection: String?,
            selectionArgs: Array<String>?
        ): String {
            uri ?: return ""
            var cursor: Cursor? = null
            val column = "_data"
            val projection = arrayOf(column)
            try {
                cursor =
                    context.contentResolver.query(uri, projection, selection, selectionArgs, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(index)
                }
            } catch (e: IllegalArgumentException) {
                val file = File(context.cacheDir, TEMP_PICTURE_NAME)
                ZaintDocumentStorage.saveFileFromUri(uri, file, context)
                return file.absolutePath
            } finally {
                cursor?.close()
            }
            return ""
        }

        private fun checkForInternet(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        }

        private fun isExternalStorageDocument(uri: Uri): Boolean =
            "com.android.externalstorage.documents" == uri.authority

        private fun isDownloadsDocument(uri: Uri): Boolean =
            "com.android.providers.downloads.documents" == uri.authority

        private fun isMediaDocument(uri: Uri): Boolean =
            "com.android.providers.media.documents" == uri.authority

        private fun isGooglePhotosUri(uri: Uri): Boolean =
            "com.google.android.apps.photos.content" == uri.authority
    }
}
