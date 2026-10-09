package de.zwegen.zpaint.test.presenter

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.DisplayMetrics
import android.view.Menu
import android.widget.Toast
import androidx.core.view.GravityCompat
import org.mockito.kotlin.any
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.ZaintSettings
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.common.CREATE_FILE_DEFAULT
import de.zwegen.zpaint.common.DEFAULT_CANVAS_HEIGHT
import de.zwegen.zpaint.common.DEFAULT_CANVAS_WIDTH
import de.zwegen.zpaint.common.LOAD_IMAGE_DEFAULT
import de.zwegen.zpaint.common.LOAD_IMAGE_IMPORT_PNG
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE_COPY
import de.zwegen.zpaint.common.PERMISSION_REQUEST_CODE_REPLACE_PICTURE
import de.zwegen.zpaint.common.REQUEST_CODE_LOAD_PICTURE
import de.zwegen.zpaint.common.SAVE_IMAGE_DEFAULT
import de.zwegen.zpaint.common.SAVE_IMAGE_FINISH
import de.zwegen.zpaint.common.SAVE_IMAGE_LOAD_NEW
import de.zwegen.zpaint.common.SAVE_IMAGE_NEW_EMPTY
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.contract.ZaintEditorContracts.Interactor
import de.zwegen.zpaint.contract.ZaintEditorContracts.MainView
import de.zwegen.zpaint.controller.ZaintToolControl
import de.zwegen.zpaint.dialog.ZaintStoragePermissionDialog
import de.zwegen.zpaint.iotasks.BitmapReturnValue
import de.zwegen.zpaint.iotasks.ZaintDocumentSaveTask.SaveImageCallback
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.model.CommandManagerModel
import de.zwegen.zpaint.presenter.ZaintEditorPresenter
import de.zwegen.zpaint.presenter.ZaintEditorPresenter.Companion.getPathFromUri
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.ui.Perspective
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MainActivityPresenterTest {
    @Mock
    private val view: MainView? = null

    @Mock
    private val model: ZaintEditorContracts.Model? = null

    @Mock
    private val navigator: ZaintEditorContracts.Navigator? = null

    @Mock
    private val interactor: Interactor? = null

    @Mock
    private val topBarViewHolder: ZaintEditorContracts.TopBarViewHolder? = null

    @Mock
    private val drawerLayoutViewHolder: ZaintEditorContracts.DrawerLayoutViewHolder? = null

    @Mock
    private val workspace: Workspace? = null

    @Mock
    private val commandSerializer: ZaintProjectSerializer? = null

    @Mock
    private val perspective: Perspective? = null

    @Mock
    private val toolController: ZaintToolControl? = null

    @Mock
    private val commandFactory: ZaintCommandFactoryApi? = null

    @Mock
    private val commandManager: ZaintCommandTimeline? = null

    @Mock
    private val bottomBarViewHolder: ZaintEditorContracts.BottomBarViewHolder? = null

    @Mock
    private val filterBarViewHolder: ZaintEditorContracts.BottomBarViewHolder? = null

    @Mock
    private val bottomNavigationViewHolder: ZaintEditorContracts.BottomNavigationViewHolder? = null

    @Mock
    private val bitmap: Bitmap? = null

    @Mock
    private val menu: Menu? = null

    @Mock
    private val sharedPreferences: ZaintSettings? = null

    @Mock
    private val context: Context? = null

    @Mock
    private val internalMemoryPath: File? = null
    private var presenter: ZaintEditorPresenter? = null
    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(model!!.colorHistory).thenReturn(de.zwegen.zpaint.colorpicker.ColorHistory())
        val layerModel = ZaintLayerModel()
        layerModel.addLayerAt(0, ZaintLayer(bitmap!!))
        Mockito.`when`(workspace!!.layerModel)
            .thenReturn(layerModel)
        val activity = Robolectric.buildActivity(ZaintEditorActivity::class.java).get()
        Mockito.`when`(context!!.resources).thenReturn(RuntimeEnvironment.getApplication().resources)
        val idlingResource = activity.idlingResource
        presenter = ZaintEditorPresenter(
            activity,
            view!!,
            model!!,
            workspace,
            navigator!!,
            interactor!!,
            topBarViewHolder!!,
            bottomBarViewHolder!!,
            filterBarViewHolder!!,
            drawerLayoutViewHolder!!,
            bottomNavigationViewHolder!!,
            commandFactory!!,
            commandManager!!,
            toolController!!,
            sharedPreferences!!, idlingResource,
            context!!, File("."), commandSerializer!!
        )
    }

    @Test
    fun testSetUp() {
        Mockito.verifyNoInteractions(
            view,
            model,
            navigator,
            interactor,
            topBarViewHolder,
            workspace,
            perspective,
            drawerLayoutViewHolder,
            commandFactory,
            commandManager,
            bottomBarViewHolder,
            filterBarViewHolder,
            bottomNavigationViewHolder,
            toolController,
            sharedPreferences,
            internalMemoryPath,
            commandSerializer
        )
    }

    @Test
    fun testNewImageClickedWhenUnchangedThenSetNewInitialState() {
        model!!.colorHistory.requestImageColors()
        val displayMetrics = DisplayMetrics()
        Mockito.`when`(view!!.displayMetrics).thenReturn(displayMetrics)
        displayMetrics.widthPixels = 300
        displayMetrics.heightPixels = 500
        val command = Mockito.mock(
            Command::class.java
        )
        Mockito.`when`(commandFactory!!.createInitCommand(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)).thenReturn(command)
        presenter!!.newImageClicked()
        Mockito.verify(commandManager)?.setInitialStateCommand(command)
        Mockito.verify(commandManager)?.reset()
        org.junit.Assert.assertFalse(model.colorHistory.needsImageColors)
        Mockito.verifyNoMoreInteractions(navigator)
    }

    @Test
    fun testNewImageClickedWhenUndoAvailableAndSavedSetNewInitialState() {
        Mockito.`when`(commandManager!!.isUndoAvailable).thenReturn(true)
        Mockito.`when`(model!!.isSaved).thenReturn(true)
        val displayMetrics = DisplayMetrics()
        Mockito.`when`(view!!.displayMetrics).thenReturn(displayMetrics)
        displayMetrics.widthPixels = 200
        displayMetrics.heightPixels = 100
        val command = Mockito.mock(
            Command::class.java
        )
        Mockito.`when`(commandFactory!!.createInitCommand(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)).thenReturn(command)
        presenter!!.newImageClicked()
        Mockito.verify(commandManager).setInitialStateCommand(command)
        Mockito.verify(commandManager).reset()
        Mockito.verifyNoMoreInteractions(navigator)
    }

    @Test
    fun testNewImageClickedWhenUndoAvailableAndNotSavedThenShowSaveBeforeNewImage() {
        Mockito.`when`(commandManager!!.isUndoAvailable).thenReturn(true)
        Mockito.`when`(model!!.isSaved).thenReturn(false)
        presenter!!.newImageClicked()
        Mockito.verify(navigator)?.showSaveBeforeNewImageDialog()
        Mockito.verifyNoMoreInteractions(navigator)
    }

    @Test
    fun testDiscardImageClickedThenClearsLayers() {
        val command = Mockito.mock(
            Command::class.java
        )
        Mockito.`when`(commandFactory!!.createResetCommand()).thenReturn(command)
        presenter!!.discardImageClicked()
        Mockito.verify(commandManager)?.addCommand(command)
        Mockito.verifyNoMoreInteractions(commandManager, navigator)
    }

    @Test
    fun testExitClickedWhenUnchangedThenFinishActivity() {
        presenter!!.exitClicked()
        Mockito.verify(navigator)?.finishActivity()
        Mockito.verifyNoMoreInteractions(navigator)
    }

    @Test
    fun testExitClickedWhenUndoAvailableThenShowSaveDialog() {
        Mockito.`when`(commandManager!!.isUndoAvailable).thenReturn(true)
        presenter!!.exitClicked()
        Mockito.verify(navigator)?.showSaveBeforeFinishDialog()
        Mockito.verifyNoMoreInteractions(navigator)
    }

    @Test
    fun testExitClickedWhenUndoAvailableAndSavedThenFinishActivity() {
        Mockito.`when`(model!!.isSaved).thenReturn(true)
        Mockito.`when`(commandManager!!.isUndoAvailable).thenReturn(true)
        presenter!!.exitClicked()
        Mockito.verify(navigator)?.finishActivity()
        Mockito.verifyNoMoreInteractions(navigator)
    }

    @Test
    fun testLoadImageClickedLoad() {
        presenter!!.replaceImageClicked()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .startLoadImageActivity(REQUEST_CODE_LOAD_PICTURE)
        Mockito.verifyNoMoreInteractions(interactor)
    }

    @Test
    fun testLoadImageClickedSaveFirst() {
        Mockito.`when`(commandManager!!.isUndoAvailable).thenReturn(true)
        Mockito.`when`(model!!.isSaved).thenReturn(false)
        presenter!!.replaceImageClicked()
        Mockito.verify(navigator)?.showSaveBeforeLoadImageDialog()
        Mockito.verifyNoMoreInteractions(interactor)
    }

    @Test
    fun testLoadNewImage() {
        presenter!!.loadNewImage()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .startLoadImageActivity(REQUEST_CODE_LOAD_PICTURE)
        Mockito.verifyNoMoreInteractions(interactor)
    }

    @Test
    fun testSaveCopyClickedThenSaveImage() {
        presenter!!.saveCopyClicked(false)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_COPY)
        Mockito.verify<Interactor?>(interactor).saveCopy(
            presenter!!, SAVE_IMAGE_DEFAULT, workspace!!.layerModel,
            commandSerializer!!, null,
            context!!
        )
        Mockito.verifyNoMoreInteractions(interactor)
    }

    @Test
    fun testSaveImageClickedThenSaveImage() {
        presenter!!.saveImageClicked()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE)
        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_DEFAULT, workspace!!.layerModel,
            commandSerializer!!, null,
            context!!
        )
        Mockito.verifyNoMoreInteractions(interactor)
    }

    @Test
    fun testOnNewImageThenResetCommandManager() {
        val metrics = Mockito.mock(DisplayMetrics::class.java)
        Mockito.`when`(view!!.displayMetrics).thenReturn(metrics)
        presenter!!.onNewImage()
        Mockito.verify(commandManager)?.reset()
    }

    @Test
    fun testOnNewImageWhenCommandReturnsThenResetPerspective() {
        val metrics = Mockito.mock(DisplayMetrics::class.java)
        Mockito.`when`(view!!.displayMetrics).thenReturn(metrics)
        presenter!!.onNewImage()
        presenter!!.onCommandPostExecute()
        Mockito.verify(workspace)?.resetPerspective()
    }

    @Test
    fun testOnNewImageThenSetInitialStateCommand() {
        val displayMetrics = DisplayMetrics()
        Mockito.`when`(view!!.displayMetrics).thenReturn(displayMetrics)
        displayMetrics.widthPixels = 300
        displayMetrics.heightPixels = 500
        val command = Mockito.mock(
            Command::class.java
        )
        Mockito.`when`(commandFactory!!.createInitCommand(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)).thenReturn(command)
        presenter!!.onNewImage()
        Mockito.verify(commandManager)?.setInitialStateCommand(command)
    }

    @Test
    fun testOnNewImageThenResetSavedPictureUri() {
        val metrics = Mockito.mock(DisplayMetrics::class.java)
        Mockito.`when`(view!!.displayMetrics).thenReturn(metrics)
        presenter!!.onNewImage()
        Mockito.verify(model)?.savedPictureUri = null
    }

    @Test
    fun testHandleActivityResultWhenUnhandledThenForwardResult() {
        val metrics = Mockito.mock(DisplayMetrics::class.java)
        Mockito.`when`(view!!.displayMetrics).thenReturn(metrics)
        val intent = Mockito.mock(Intent::class.java)
        presenter!!.handleActivityResult(0, Activity.RESULT_OK, intent)
        Mockito.verify(view).superHandleActivityResult(0, Activity.RESULT_OK, intent)
    }

    @Test
    fun testHandleActivityResultWhenUnhandledAndResultNotOKThenForwardResult() {
        val metrics = Mockito.mock(DisplayMetrics::class.java)
        Mockito.`when`(view!!.displayMetrics).thenReturn(metrics)
        val intent = Mockito.mock(Intent::class.java)
        presenter!!.handleActivityResult(0, Activity.RESULT_CANCELED, intent)
        Mockito.verify(view).superHandleActivityResult(0, Activity.RESULT_CANCELED, intent)
    }

    @Test
    fun testHandleActivityResultWhenLoadPictureThenLoadIntentPicture() {
        val metrics = Mockito.mock(DisplayMetrics::class.java)
        metrics.widthPixels = 13
        metrics.heightPixels = 17
        Mockito.`when`(view!!.displayMetrics).thenReturn(metrics)
        val intent = Mockito.mock(Intent::class.java)
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(intent.data).thenReturn(uri)
        presenter!!.handleActivityResult(REQUEST_CODE_LOAD_PICTURE, Activity.RESULT_OK, intent)
        Mockito.verify<Interactor?>(interactor).loadFile(
            presenter!!, LOAD_IMAGE_DEFAULT, uri,
            context!!, false,
            commandSerializer!!
        )
    }

    @Test
    fun testHandleActivityResultWhenResultNotOkThenDoNothing() {
        val metrics = Mockito.mock(DisplayMetrics::class.java)
        Mockito.`when`(view!!.displayMetrics).thenReturn(metrics)
        val intent = Mockito.mock(Intent::class.java)
        presenter!!.handleActivityResult(0, Activity.RESULT_CANCELED, intent)
        Mockito.verifyNoInteractions(interactor, navigator)
    }

    @Test
    fun testOnBackPressedWhenUntouchedThenFinishActivity() {
        Mockito.`when`(toolController!!.isDefaultTool).thenReturn(true)
        presenter!!.onBackPressed()
        Mockito.verify(navigator)?.finishActivity()
    }

    @Test
    fun testOnBackPressedWhenStartDrawerOpenThenCloseDrawer() {
        Mockito.`when`(drawerLayoutViewHolder!!.isDrawerOpen(GravityCompat.START)).thenReturn(true)
        presenter!!.onBackPressed()
        Mockito.verify(drawerLayoutViewHolder).closeDrawer(GravityCompat.START, true)
    }

    @Test
    fun testOnBackPressedWhenEndDrawerOpenThenCloseDrawer() {
        Mockito.`when`(drawerLayoutViewHolder!!.isDrawerOpen(GravityCompat.END)).thenReturn(true)
        presenter!!.onBackPressed()
        Mockito.verify(drawerLayoutViewHolder).closeDrawer(GravityCompat.END, true)
    }

    @Test
    fun testSaveImageConfirmClickedThenSaveImage() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.saveImageConfirmClicked(0, uri)
        Mockito.verify(interactor)?.saveImage(
            presenter!!, 0, workspace!!.layerModel,
            commandSerializer!!, uri,
            context!!
        )
    }

    @Test
    fun testSaveImageConfirmClickedThenUseRequestCode() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.saveImageConfirmClicked(-1, uri)
        Mockito.verify(interactor)?.saveImage(
            presenter!!, -1, workspace!!.layerModel,
            commandSerializer!!, uri,
            context!!
        )
    }

    @Test
    fun testSaveCopyConfirmCLickedThenSaveImage() {
        presenter!!.saveCopyConfirmClicked(0, null)
        Mockito.verify(interactor)?.saveCopy(
            presenter!!, 0, workspace!!.layerModel,
            commandSerializer!!, null,
            context!!
        )
    }

    @Test
    fun testSaveCopyConfirmClickedThenUseRequestCode() {
        presenter!!.saveCopyConfirmClicked(-1, null)
        Mockito.verify(interactor)?.saveCopy(
            presenter!!, -1, workspace!!.layerModel,
            commandSerializer!!, null,
            context!!
        )
    }

    @Test
    fun testUndoClickedWhenKeyboardOpenedThenCloseKeyboard() {
        Mockito.`when`(view!!.isKeyboardShown).thenReturn(true)
        presenter!!.undoClicked()
        Mockito.verify(view).hideKeyboard()
        Mockito.verifyNoInteractions(commandManager)
    }

    @Test
    fun testUndoClickedThenExecuteUndo() {
        presenter!!.undoClicked()
        Mockito.verify(commandManager)?.undo()
    }

    @Test
    fun testRedoClickedWhenKeyboardOpenedThenCloseKeyboard() {
        Mockito.`when`(view!!.isKeyboardShown).thenReturn(true)
        presenter!!.redoClicked()
        Mockito.verify(view).hideKeyboard()
        Mockito.verifyNoInteractions(commandManager)
    }

    @Test
    fun testRedoClickedThenExecuteRedo() {
        presenter!!.redoClicked()
        Mockito.verify(commandManager)?.redo()
    }

    @Test
    fun testShowLayerMenuClickedThenShowLayerDrawer() {
        presenter!!.showLayerMenuClicked()
        Mockito.verify(drawerLayoutViewHolder)?.openDrawer(GravityCompat.END)
    }

    @Test
    fun testOnCommandPostExecuteThenSetModelUnsaved() {
        presenter!!.onCommandPostExecute()
        Mockito.verify(model)?.isSaved = false
    }

    @Test
    fun testOnCommandPostExecuteThenResetInternalToolState() {
        presenter!!.onCommandPostExecute()
        Mockito.verify(toolController)?.resetToolInternalState()
    }

    @Test
    fun testOnCommandPostExecuteThenRefreshDrawingSurface() {
        presenter!!.onCommandPostExecute()
        Mockito.verify(view)?.refreshDrawingSurface()
    }

    @Test
    fun testOnCommandPostExecuteThenSetUndoRedoButtons() {
        presenter!!.onCommandPostExecute()
        Mockito.verify(topBarViewHolder)?.disableRedoButton()
        Mockito.verify(topBarViewHolder)?.disableUndoButton()
    }

    @Test
    fun testSetTopBarColorThenSetColorButtonColor() {
        presenter!!.setBottomNavigationColor(Color.GREEN)
        Mockito.verify(bottomNavigationViewHolder)?.setColorButtonColor(Color.GREEN)
    }

    @Test
    fun testInitializeFromCleanStateWhenDefaultThenUnsetSavedPictureUri() {
        presenter!!.initializeFromCleanState()
        Mockito.verify(model)?.savedPictureUri = null
    }

    @Test
    fun testCleanCanvasCancelsPendingImageColors() {
        model!!.colorHistory.requestImageColors()
        presenter!!.initializeFromCleanState()
        org.junit.Assert.assertFalse(model.colorHistory.needsImageColors)
    }

    @Test
    fun testInitializeFromCleanStateWhenDefaultThenResetTool() {
        presenter!!.initializeFromCleanState()
        Mockito.verify(toolController)?.resetToolInternalStateOnImageLoaded()
    }

    @Test
    fun testRestoreStateThenRestoreFragmentListeners() {
        presenter!!.restoreState(false, null, null)
        Mockito.verify(navigator)?.restoreFragmentListeners()
    }

    @Test
    fun testRestoreStateThenSetModel() {
        val savedPictureUri = Mockito.mock(Uri::class.java)
        val cameraImageUri = Mockito.mock(Uri::class.java)
        presenter!!.restoreState(false, savedPictureUri, cameraImageUri)
        Mockito.verify(model)?.isSaved = false
        Mockito.verify(model)?.savedPictureUri = savedPictureUri
        Mockito.verify(model)?.cameraImageUri = cameraImageUri
    }

    @Test
    fun testRestoreStateThenResetTool() {
        presenter!!.restoreState(false, null, null)
        Mockito.verify(toolController)?.resetToolInternalStateOnImageLoaded()
    }

    @Test
    fun testOnCreateToolCallsToolController() {
        presenter!!.onCreateTool()
        Mockito.verify(toolController)?.createTool()
        Mockito.verifyNoMoreInteractions(toolController)
    }

    @Test
    fun testFinishInitializeThenSetUndoRedoButtons() {
        presenter!!.finishInitialize()
        Mockito.verify(topBarViewHolder)?.disableUndoButton()
        Mockito.verify(topBarViewHolder)?.disableRedoButton()
    }

    @Test
    fun testFinishInitializeWhenUndoAvailableThenSetUndoRedoButtons() {
        Mockito.`when`(commandManager!!.isUndoAvailable).thenReturn(true)
        presenter!!.finishInitialize()
        Mockito.verify(topBarViewHolder)?.enableUndoButton()
        Mockito.verify(topBarViewHolder)?.disableRedoButton()
    }

    @Test
    fun testFinishInitializeWhenRedoAvailableThenSetUndoRedoButtons() {
        Mockito.`when`(commandManager!!.isRedoAvailable).thenReturn(true)
        presenter!!.finishInitialize()
        Mockito.verify(topBarViewHolder)?.disableUndoButton()
        Mockito.verify(topBarViewHolder)?.enableRedoButton()
    }

    @Test
    fun testFinishInitializeThenRestoreColorButtonColor() {
        Mockito.`when`(toolController!!.toolColor).thenReturn(Color.RED)
        presenter!!.finishInitialize()
        Mockito.verify(bottomNavigationViewHolder)?.setColorButtonColor(Color.RED)
    }

    @Test
    fun testFinishInitializeWhenDefaultThenInitializeActionBarDefault() {
        presenter!!.finishInitialize()
        Mockito.verify(view)?.initializeActionBar()
    }

    @Test
    fun testFinishInitializeWhenCommandManagerBusyRestoreProgressDialog() {
        Mockito.`when`(commandManager!!.isBusy).thenReturn(true)
        presenter!!.finishInitialize()
        Mockito.verify(navigator)?.showIndeterminateProgressDialog()
    }

    @Test
    fun testFinishInitializeWhenCommandManagerIdleThenDoNothing() {
        presenter!!.finishInitialize()
        Mockito.verify(navigator, Mockito.never())?.showIndeterminateProgressDialog()
    }

    @Test
    fun testToolClickedWhenSameToolTypeThenToggleOptions() {
        Mockito.`when`(toolController!!.toolType).thenReturn(ZaintToolKind.TEXT)
        Mockito.`when`(toolController.hasToolOptionsView()).thenReturn(true)
        presenter!!.toolClicked(ZaintToolKind.TEXT)
        Mockito.verify(toolController).toggleToolOptionsView()
    }

    @Test
    fun testCurrentToolClickedThenToggleOptions() {
        Mockito.`when`(toolController!!.hasToolOptionsView()).thenReturn(true)

        presenter!!.actionCurrentToolClicked()

        Mockito.verify(toolController).toggleToolOptionsView()
    }

    @Test
    fun testToolClickedWhenKeyboardShownThenHideKeyboard() {
        Mockito.`when`(view!!.isKeyboardShown).thenReturn(true)
        presenter!!.toolClicked(ZaintToolKind.ERASER)
        Mockito.verify(view).hideKeyboard()
    }

    @Test
    fun testOnCreateFilePostExecuteWhenFailedThenShowDialog() {
        presenter!!.onCreateFilePostExecute(CREATE_FILE_DEFAULT, null)
        Mockito.verify(navigator)?.showSaveErrorDialog()
    }

    @Test
    fun testOnCreateFilePostExecuteWhenDefaultThenSetUri() {
        val file = Mockito.mock(File::class.java)
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(view!!.getUriFromFile(file)).thenReturn(uri)
        presenter!!.onCreateFilePostExecute(CREATE_FILE_DEFAULT, file)
        Mockito.verify(model)?.savedPictureUri = uri
        Mockito.verifyNoInteractions(navigator)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOnCreateFilePostExecuteWhenInvalidRequestThenThrowException() {
        val file = Mockito.mock(File::class.java)
        presenter!!.onCreateFilePostExecute(0, file)
    }

    @Test
    fun testOnLoadImagePreExecuteDoesNothing() {
        presenter!!.onLoadImagePreExecute(LOAD_IMAGE_DEFAULT)
        Mockito.verifyNoInteractions(
            view, model, navigator, interactor, topBarViewHolder, workspace, perspective,
            drawerLayoutViewHolder, commandFactory, commandManager, bottomBarViewHolder,
            toolController
        )
    }

    @Test
    fun testOnLoadImagePostExecuteWhenFailedThenShowDialog() {
        presenter!!.onLoadImagePostExecute(0, null, null)
        Mockito.verify(navigator)?.showLoadErrorDialog()
    }

    @Test
    fun testOnLoadImagePostExecuteWhenDefaultThenResetModelUris() {
        val uri = Mockito.mock(Uri::class.java)
        val bitmap = Mockito.mock(Bitmap::class.java)
        val returnValue = BitmapReturnValue(null, bitmap, false)
        presenter!!.onLoadImagePostExecute(LOAD_IMAGE_DEFAULT, uri, returnValue)
        Mockito.verify(model)?.savedPictureUri = uri
        Mockito.verify(model)?.cameraImageUri = null
        org.junit.Assert.assertTrue(model!!.colorHistory.needsImageColors)
    }

    @Test
    fun testOnLoadZaintProjectThenResetModelUris() {
        val uri = Mockito.mock(Uri::class.java)
        val projectModel = CommandManagerModel(Mockito.mock(Command::class.java), mutableListOf())
        val returnValue = BitmapReturnValue(projectModel, null)

        presenter!!.onLoadImagePostExecute(LOAD_IMAGE_DEFAULT, uri, returnValue)

        Mockito.verify(model)?.savedPictureUri = uri
        Mockito.verify(model)?.cameraImageUri = null
    }

    @Test
    fun testOnLoadImagePostExecuteWhenDefaultThenResetCommandManager() {
        val uri = Mockito.mock(Uri::class.java)
        val bitmap = Mockito.mock(Bitmap::class.java)
        val returnValue = BitmapReturnValue(null, bitmap, false)
        val command = Mockito.mock(
            Command::class.java
        )
        Mockito.`when`(commandFactory!!.createInitCommand(bitmap)).thenReturn(command)
        presenter!!.onLoadImagePostExecute(LOAD_IMAGE_DEFAULT, uri, returnValue)
        Mockito.verify(commandManager)?.setInitialStateCommand(command)
        Mockito.verify(commandManager)?.reset()
        Mockito.verifyNoMoreInteractions(commandManager)
        Mockito.verify(toolController)?.resetToolInternalStateOnImageLoaded()
    }

    @Test
    fun testOnLoadImagePostExecuteWhenImportThenSetBitmap() {
        val uri = Mockito.mock(Uri::class.java)
        val bitmap = Mockito.mock(Bitmap::class.java)
        val returnValue = BitmapReturnValue(null, bitmap, false)
        Mockito.`when`(toolController!!.toolType).thenReturn(ZaintToolKind.IMPORTPNG)
        presenter!!.onLoadImagePostExecute(LOAD_IMAGE_IMPORT_PNG, uri, returnValue)
        Mockito.verify(toolController).setBitmapFromSource(bitmap)
        Mockito.verifyNoInteractions(commandManager)
    }

    @Test
    fun testOnLoadImagePostExecuteWhenImportAndNotImportToolSetThenIgnore() {
        val uri = Mockito.mock(Uri::class.java)
        val bitmap = Mockito.mock(Bitmap::class.java)
        val returnValue = BitmapReturnValue(null, bitmap, false)
        presenter!!.onLoadImagePostExecute(LOAD_IMAGE_IMPORT_PNG, uri, returnValue)
        Mockito.verify(toolController, Mockito.never())?.setBitmapFromSource(
            ArgumentMatchers.any(
                Bitmap::class.java
            )
        )
        Mockito.verifyNoInteractions(commandManager)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOnLoadImagePostExecuteWhenInvalidRequestThenThrowException() {
        val uri = Mockito.mock(Uri::class.java)
        val bitmap = Mockito.mock(Bitmap::class.java)
        val returnValue = BitmapReturnValue(null, bitmap, false)
        presenter!!.onLoadImagePostExecute(0, uri, returnValue)
    }

    @Test
    fun testOnSaveImagePreExecuteThenShowProgressDialog() {
        presenter!!.onSaveImagePreExecute(0)
        Mockito.verify(view)?.showContentLoadingProgressBar()
    }

    @Test
    fun testOnSaveImagePostExecuteWhenFailedThenShowDialog() {
        presenter!!.onSaveImagePostExecute(0, null, false)
        Mockito.verify(navigator)?.showSaveErrorDialog()
    }

    @Test
    fun testHandlePermissionResultLoadPermissionGranted() {
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_REQUEST_CODE_REPLACE_PICTURE, arrayOf<String>(
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .startLoadImageActivity(REQUEST_CODE_LOAD_PICTURE)
    }

    @Test
    fun testHandlePermissionResultLoadPermissionPermanentlyDenied() {
        val permission = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(true)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_REQUEST_CODE_REPLACE_PICTURE,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify(navigator).showRequestPermanentlyDeniedPermissionRationaleDialog()
    }

    @Test
    fun testHandlePermissionResultLoadPermissionNotGranted() {
        val permission = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(false)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_REQUEST_CODE_REPLACE_PICTURE,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showRequestPermissionRationaleDialog(
                ZaintStoragePermissionDialog.StoragePermissionRequest.EXTERNAL_STORAGE,
                permission,
                PERMISSION_REQUEST_CODE_REPLACE_PICTURE
            )
    }

    @Test
    fun testHandlePermissionResultSavePermissionGranted() {
        Mockito.`when`(workspace!!.layerModel).thenReturn(
            Mockito.mock(
                ZaintLayerModel::class.java
            )
        )
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE, arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify<Interactor?>(interactor).saveImage(
            any<SaveImageCallback>(),
            ArgumentMatchers.eq(SAVE_IMAGE_DEFAULT),
            any<ZaintLayerModel>(),
            any<ZaintProjectSerializer>(),
            ArgumentMatchers.eq<Uri?>(null),
            any<Context>(),
        )
    }

    @Test
    fun testHandlePermissionResultSavePermissionPermanentlyDenied() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(true)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify(navigator).showRequestPermanentlyDeniedPermissionRationaleDialog()
    }

    @Test
    fun testHandlePermissionResultSavePermissionNotGranted() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(false)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showRequestPermissionRationaleDialog(
                ZaintStoragePermissionDialog.StoragePermissionRequest.EXTERNAL_STORAGE,
                permission,
                PERMISSION_EXTERNAL_STORAGE_SAVE
            )
    }

    @Test
    fun testHandlePermissionResultSaveCopyPermissionGranted() {
        Mockito.`when`(workspace!!.layerModel).thenReturn(
            Mockito.mock(
                ZaintLayerModel::class.java
            )
        )
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_COPY, arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify<Interactor?>(interactor).saveCopy(
            any<SaveImageCallback>(),
            ArgumentMatchers.eq(SAVE_IMAGE_DEFAULT),
            any<ZaintLayerModel>(),
            any<ZaintProjectSerializer>(),
            ArgumentMatchers.eq<Uri?>(null),
            any<Context>(),
        )
    }

    @Test
    fun testHandlePermissionResultSaveCopyPermissionNotGranted() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(false)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showRequestPermissionRationaleDialog(
                ZaintStoragePermissionDialog.StoragePermissionRequest.EXTERNAL_STORAGE,
                permission,
                PERMISSION_EXTERNAL_STORAGE_SAVE_COPY
            )
    }

    @Test
    fun testHandlePermissionResultSaveCopyPermissionPermanentlyDenied() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(true)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify(navigator).showRequestPermanentlyDeniedPermissionRationaleDialog()
    }

    @Test
    fun testHandlePermissionResultSaveBeforeFinishPermissionGranted() {
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH, arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_FINISH, workspace!!.layerModel,
            commandSerializer!!, ZaintDocumentStorage.storeImageUri,
            context!!
        )
    }

    @Test
    fun testHandlePermissionResultSaveBeforeFinishPermissionNotGranted() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(false)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showRequestPermissionRationaleDialog(
                ZaintStoragePermissionDialog.StoragePermissionRequest.EXTERNAL_STORAGE,
                permission,
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH
            )
    }

    @Test
    fun testHandlePermissionResultSaveBeforeFinishPermissionPermanentlyDenied() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(true)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify(navigator).showRequestPermanentlyDeniedPermissionRationaleDialog()
    }

    @Test
    fun testHandlePermissionResultSaveBeforeLoadNewPermissionNotGranted() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(false)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showRequestPermissionRationaleDialog(
                ZaintStoragePermissionDialog.StoragePermissionRequest.EXTERNAL_STORAGE,
                permission,
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW
            )
    }

    @Test
    fun testHandlePermissionResultSaveBeforeLoadNewPermissionPermanentlyDenied() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(true)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify(navigator).showRequestPermanentlyDeniedPermissionRationaleDialog()
    }

    @Test
    fun testHandlePermissionResultSaveBeforeLoadNewPermissionGranted() {
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW, arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_LOAD_NEW, workspace!!.layerModel,
            commandSerializer!!, ZaintDocumentStorage.storeImageUri,
            context!!
        )
    }

    @Test
    fun testHandlePermissionResultSaveBeforeNewEmptyPermissionNotGranted() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(false)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showRequestPermissionRationaleDialog(
                ZaintStoragePermissionDialog.StoragePermissionRequest.EXTERNAL_STORAGE,
                permission,
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY
            )
    }

    @Test
    fun testHandlePermissionResultSaveBeforeNewEmptyPermissionPermanentlyDenied() {
        val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Mockito.`when`(navigator!!.isPermissionPermanentlyDenied(permission)).thenReturn(true)
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY,
            permission, intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        Mockito.verify(navigator).showRequestPermanentlyDeniedPermissionRationaleDialog()
    }

    @Test
    fun testHandlePermissionResultSaveBeforeNewEmptyPermissionGranted() {
        presenter!!.handleRequestPermissionsResult(
            PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY, arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_NEW_EMPTY, workspace!!.layerModel,
            commandSerializer!!, ZaintDocumentStorage.storeImageUri,
            context!!
        )
    }

    @Test
    fun testHandlePermissionResultWhenStoragePermissionGrantedAndRequestCodeUnknownThenCallBaseHandle() {
        presenter!!.handleRequestPermissionsResult(
            100,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify(view)?.superHandleRequestPermissionsResult(
            100,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
    }

    @Test
    fun testHandlePermissionResultWhenCameraPermissionGrantedAndRequestCodeUnknownThenCallBaseHandle() {
        presenter!!.handleRequestPermissionsResult(
            123,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        Mockito.verify(view)?.superHandleRequestPermissionsResult(
            123,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
    }

    @Test
    fun testHandlePermissionResultWhenMultiplePermissionsThenCallBaseHandle() {
        presenter!!.handleRequestPermissionsResult(
            456,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.CAMERA
            ),
            intArrayOf(
                PackageManager.PERMISSION_GRANTED,
                PackageManager.PERMISSION_DENIED
            )
        )
        Mockito.verify(view)?.superHandleRequestPermissionsResult(
            456,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED)
        )
    }

    @Test
    fun testOnNavigationItemSelectedSaveCopyPermissionGranted() {
        presenter!!.saveCopyClicked(false)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_COPY)
        Mockito.verify<Interactor?>(interactor).saveCopy(
            presenter!!, SAVE_IMAGE_DEFAULT, workspace!!.layerModel,
            commandSerializer!!, null,
            context!!
        )
    }

    @Test
    fun testOnNavigationItemSelectedSaveCopyPermissionNotGranted() {
        Mockito.`when`(navigator!!.doIHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .thenReturn(false)
        Mockito.`when`(navigator.isSdkAboveOrEqualM).thenReturn(false).thenReturn(true)
        presenter!!.saveCopyClicked(false)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_COPY,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_COPY)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator).askForPermission(
            arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), PERMISSION_EXTERNAL_STORAGE_SAVE_COPY
        )
    }

    @Test
    fun testOnNavigationItemSelectedSavePermissionGranted() {
        presenter!!.saveImageClicked()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE)
        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_DEFAULT, workspace!!.layerModel,
            commandSerializer!!, ZaintDocumentStorage.storeImageUri,
            context!!
        )
    }

    @Test
    fun testOnNavigationItemSelectedSavePermissionNotGranted() {
        Mockito.`when`(navigator!!.doIHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .thenReturn(false)
        Mockito.`when`(navigator.isSdkAboveOrEqualM).thenReturn(false).thenReturn(true)
        presenter!!.saveImageClicked()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator).askForPermission(
            arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), PERMISSION_EXTERNAL_STORAGE_SAVE
        )
    }

    @Test
    fun testSaveAndFinishPermissionGranted() {
        presenter!!.saveBeforeFinish()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH)
        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_FINISH, workspace!!.layerModel,
            commandSerializer!!, ZaintDocumentStorage.storeImageUri,
            context!!
        )
    }

    @Test
    fun testSaveAndFinishPermissionNotGranted() {
        Mockito.`when`(navigator!!.doIHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .thenReturn(false)
        Mockito.`when`(navigator.isSdkAboveOrEqualM).thenReturn(false).thenReturn(true)
        presenter!!.saveBeforeFinish()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator).askForPermission(
            arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_FINISH
        )
    }

    @Test
    fun testSaveAndNewImagePermissionGranted() {
        presenter!!.saveBeforeNewImage()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY)
        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_NEW_EMPTY, workspace!!.layerModel,
            commandSerializer!!, ZaintDocumentStorage.storeImageUri,
            context!!
        )
    }

    @Test
    fun testSaveAndNewImagePermissionNotGranted() {
        Mockito.`when`(navigator!!.doIHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .thenReturn(false)
        Mockito.`when`(navigator.isSdkAboveOrEqualM).thenReturn(false).thenReturn(true)
        presenter!!.saveBeforeNewImage()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator).askForPermission(
            arrayOf<String>(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_NEW_EMPTY
        )
    }

    @Test
    fun testSaveAndLoadImagePermissionGranted() {
        presenter!!.saveBeforeLoadImage()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showSaveImageInformationDialogWhenStandalone(
                PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW,
                sharedPreferences!!.nextImageNumber,
                false
            )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW)

        Mockito.verify<Interactor?>(interactor).saveImage(
            presenter!!, SAVE_IMAGE_LOAD_NEW, workspace!!.layerModel,
            commandSerializer!!, ZaintDocumentStorage.storeImageUri,
            context!!
        )
    }

    @Test
    fun testSaveAndLoadImagePermissionNotGranted() {
        Mockito.`when`(navigator!!.doIHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                .thenReturn(false)
        Mockito.`when`(navigator.isSdkAboveOrEqualM).thenReturn(false).thenReturn(true)
        presenter!!.saveBeforeLoadImage()
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
                .showSaveImageInformationDialogWhenStandalone(
                        PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW,
                        sharedPreferences!!.nextImageNumber,
                        false
                )
        presenter!!.switchBetweenVersions(PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator).askForPermission(
                arrayOf<String>(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), PERMISSION_EXTERNAL_STORAGE_SAVE_CONFIRMED_LOAD_NEW
        )
    }

    @Test
    fun testOnSaveImagePostExecuteThenDismissProgressDialog() {
        presenter!!.onSaveImagePostExecute(0, null, false)
        Mockito.verify(view)?.hideContentLoadingProgressBar()
    }

    @Test
    fun testOnSaveImagePostExecuteWhenNotSavedAsCopyThenShowSaveToast() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_FINISH, uri, false)
        val path = getPathFromUri(context!!, uri)
        Mockito.verify(navigator)?.showToast(
            context.getString(R.string.saved_to) + path, Toast.LENGTH_LONG
        )
    }

    @Test
    fun testOnSaveImagePostExecuteWhenNotSavedAsCopyThenSetModelUri() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_FINISH, uri, false)
        Mockito.verify(model)?.savedPictureUri = uri
        Mockito.verify(model)?.isSaved = true
    }

    @Test
    fun testOnSaveImagePostExecuteWhenSavedAsCopyThenShowCopyToast() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_FINISH, uri, true)
        val path = getPathFromUri(context!!, uri)
        Mockito.verify(navigator)?.showToast(
            context.getString(R.string.copy_to) + path, Toast.LENGTH_LONG
        )
    }

    @Test
    fun testOnSaveImagePostExecuteWhenSavedAsCopyThenDoNotTouchModel() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_FINISH, uri, true)
        Mockito.verify(model, Mockito.never())?.savedPictureUri = ArgumentMatchers.any(
            Uri::class.java
        )
        Mockito.verify(model, Mockito.never())?.cameraImageUri = ArgumentMatchers.any(
            Uri::class.java
        )
        Mockito.verify(model, Mockito.never())?.isSaved = ArgumentMatchers.anyBoolean()
    }

    @Test
    fun testOnSaveImagePostExecuteWhenSavedThenBroadcastToPictureGallery() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_DEFAULT, uri, false)
        Mockito.verify(navigator)?.broadcastAddPictureToGallery(uri)
    }

    @Test
    fun testOnSaveImagePostExecuteWhenSavedAsCopyThenBroadcastToPictureGallery() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_DEFAULT, uri, true)
        Mockito.verify(navigator)?.broadcastAddPictureToGallery(uri)
    }

    @Test
    fun testOnSaveImagePostExecuteWhenChooseNewThenSetNewInitialState() {
        val uri = Mockito.mock(Uri::class.java)
        val displayMetrics = DisplayMetrics()
        Mockito.`when`(view!!.displayMetrics).thenReturn(displayMetrics)
        displayMetrics.widthPixels = 300
        displayMetrics.heightPixels = 500
        val command = Mockito.mock(
            Command::class.java
        )
        Mockito.`when`(commandFactory!!.createInitCommand(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT)).thenReturn(command)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_NEW_EMPTY, uri, false)
        Mockito.verify(commandManager)?.setInitialStateCommand(command)
        Mockito.verify(commandManager)?.reset()
    }

    @Test
    fun testOnSaveImagePostExecuteWhenDefaultThenDoNotShowDialog() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_DEFAULT, uri, false)
        Mockito.verify(navigator, Mockito.never())?.startLoadImageActivity(anyInt())
        Mockito.verify(navigator, Mockito.never())?.finishActivity()
    }

    @Test
    fun testOnSaveImagePostExecuteWhenFinishThenFinishActivity() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_FINISH, uri, false)
        Mockito.verify(navigator)?.finishActivity()
    }

    @Test
    fun testOnSaveImagePostExecuteWhenLoadThenStartActivity() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(SAVE_IMAGE_LOAD_NEW, uri, false)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .startLoadImageActivity(REQUEST_CODE_LOAD_PICTURE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testOnSaveImagePostExecuteWhenInvalidRequestThenThrowException() {
        val uri = Mockito.mock(Uri::class.java)
        presenter!!.onSaveImagePostExecute(0, uri, false)
    }

    @Test
    fun testGetContentResolver() {
        val resolver = Mockito.mock(ContentResolver::class.java)
        Mockito.`when`(view!!.myContentResolver).thenReturn(resolver)
        val result = presenter!!.contentResolver
        Assert.assertEquals(resolver, result)
    }

    @Test
    fun testIsFinishing() {
        Mockito.`when`(view!!.finishing).thenReturn(true, false)
        Assert.assertTrue(presenter!!.isFinishing)
        Assert.assertFalse(presenter!!.isFinishing)
    }

    @Test
    fun testShowScaleDialogWhenNotEnoughMemory() {
        val bmr = BitmapReturnValue(
            workspace!!.layerModel.layers,
            bitmap,
            true
        )
        presenter!!.onLoadImagePostExecute(LOAD_IMAGE_IMPORT_PNG, null, bmr)
        Mockito.verify<ZaintEditorContracts.Navigator?>(navigator)
            .showScaleImageRequestDialog(null, LOAD_IMAGE_IMPORT_PNG)
    }
}
