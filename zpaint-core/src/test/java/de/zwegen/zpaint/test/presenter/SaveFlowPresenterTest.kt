package de.zwegen.zpaint.test.presenter

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.graphics.Bitmap
import android.net.Uri
import de.zwegen.zpaint.ZaintDocumentStorage
import de.zwegen.zpaint.ZaintDocumentStorage.FileType.JPG
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.ZaintSettings
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.common.PERMISSION_EXTERNAL_STORAGE_SAVE
import de.zwegen.zpaint.common.REQUEST_CODE_CREATE_SAVE_FILE
import de.zwegen.zpaint.common.SAVE_IMAGE_DEFAULT
import de.zwegen.zpaint.contract.ZaintEditorContracts
import de.zwegen.zpaint.controller.ZaintToolControl
import de.zwegen.zpaint.dialog.SaveDialogFileName
import de.zwegen.zpaint.iotasks.ZaintDocumentSaveTask.SaveImageCallback
import de.zwegen.zpaint.model.ZaintLayer
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.presenter.ZaintEditorPresenter
import de.zwegen.zpaint.tools.Workspace
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.io.File

@RunWith(MockitoJUnitRunner.Silent::class)
class SaveFlowPresenterTest {
    @Mock private lateinit var view: ZaintEditorContracts.MainView
    @Mock private lateinit var model: ZaintEditorContracts.Model
    @Mock private lateinit var workspace: Workspace
    @Mock private lateinit var navigator: ZaintEditorContracts.Navigator
    @Mock private lateinit var interactor: ZaintEditorContracts.Interactor
    @Mock private lateinit var topBar: ZaintEditorContracts.TopBarViewHolder
    @Mock private lateinit var bottomBar: ZaintEditorContracts.BottomBarViewHolder
    @Mock private lateinit var filterBar: ZaintEditorContracts.BottomBarViewHolder
    @Mock private lateinit var drawer: ZaintEditorContracts.DrawerLayoutViewHolder
    @Mock private lateinit var bottomNavigation: ZaintEditorContracts.BottomNavigationViewHolder
    @Mock private lateinit var commandFactory: ZaintCommandFactoryApi
    @Mock private lateinit var commandManager: ZaintCommandTimeline
    @Mock private lateinit var toolController: ZaintToolControl
    @Mock private lateinit var preferences: ZaintSettings
    @Mock private lateinit var context: Context
    @Mock private lateinit var contentResolver: ContentResolver
    @Mock private lateinit var commandSerializer: ZaintProjectSerializer
    @Mock private lateinit var bitmap: Bitmap

    private lateinit var presenter: ZaintEditorPresenter

    @Before
    fun setUp() {
        Mockito.`when`(view.myContentResolver).thenReturn(contentResolver)
        val layerModel = ZaintLayerModel().apply { addLayerAt(0, ZaintLayer(bitmap)) }
        Mockito.`when`(workspace.layerModel).thenReturn(layerModel)
        presenter = ZaintEditorPresenter(
            null,
            view,
            model,
            workspace,
            navigator,
            interactor,
            topBar,
            bottomBar,
            filterBar,
            drawer,
            bottomNavigation,
            commandFactory,
            commandManager,
            toolController,
            preferences,
            CountingIdlingResource("save-flow"),
            context,
            File("."),
            commandSerializer
        )
    }

    @Test
    fun saveWithCurrentTargetOpensSaveAsDialog() {
        val target = Mockito.mock(Uri::class.java)
        val permission = Mockito.mock(UriPermission::class.java)
        Mockito.`when`(model.savedPictureUri).thenReturn(target)
        Mockito.`when`(permission.uri).thenReturn(target)
        Mockito.`when`(permission.isWritePermission).thenReturn(true)
        Mockito.`when`(contentResolver.persistedUriPermissions).thenReturn(listOf(permission))

        presenter.saveImageClicked()

        Mockito.verify(navigator).showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            preferences.nextImageNumber,
            false
        )
        Mockito.verifyNoInteractions(interactor)
    }

    @Test
    fun saveWithCurrentTargetWithoutWritePermissionOpensSaveAsDialog() {
        val target = Mockito.mock(Uri::class.java)
        Mockito.`when`(model.savedPictureUri).thenReturn(target)

        presenter.saveImageClicked()

        Mockito.verify(navigator).showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            preferences.nextImageNumber,
            false
        )
        Mockito.verifyNoInteractions(interactor)
    }

    @Test
    fun saveWithoutCurrentTargetOpensSaveAsDialog() {
        Mockito.`when`(model.savedPictureUri).thenReturn(null)

        presenter.saveImageClicked()

        Mockito.verify(navigator).showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            preferences.nextImageNumber,
            false
        )
    }

    @Test
    fun saveAsSuccessUpdatesCurrentTarget() {
        val target = Mockito.mock(Uri::class.java)

        presenter.onSaveImagePostExecute(SAVE_IMAGE_DEFAULT, target, false)

        Mockito.verify(model).savedPictureUri = target
    }

    @Test
    fun selectingFolderCreatesDocumentAndSavesItsConfirmedUriDirectly() {
        ZaintDocumentStorage.filename = "chosen-name"
        ZaintDocumentStorage.fileType = JPG
        val target = Mockito.mock(Uri::class.java)
        val result = Mockito.mock(Intent::class.java)
        Mockito.`when`(result.data).thenReturn(target)

        presenter.selectSaveFolderClicked()

        Mockito.verify(navigator).startCreateSaveDocumentActivity(
            REQUEST_CODE_CREATE_SAVE_FILE,
            "chosen-name.jpg",
            "image/jpeg"
        )

        presenter.handleActivityResult(REQUEST_CODE_CREATE_SAVE_FILE, Activity.RESULT_OK, result)

        Mockito.verify(interactor).saveImage(
            presenter as SaveImageCallback,
            SAVE_IMAGE_DEFAULT,
            workspace.layerModel,
            commandSerializer,
            target,
            context
        )
        Mockito.verify(navigator, Mockito.never()).showSaveImageInformationDialogWhenStandalone(
            Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()
        )
    }

    @Test
    fun saveAsWithExistingUriAlwaysUsesPicturesFolderPrefix() {
        val fileName = SaveDialogFileName(null, "Strand.png", true)

        org.junit.Assert.assertEquals("Pictures: ", fileName.folderPrefix)
    }

    @Test
    fun saveDialogPrefixIsSeparateFromEditableFilenameAndFileIoFilename() {
        val fileName = SaveDialogFileName("Urlaub", "Strand.png", true)

        fileName.applyToFileIo()

        org.junit.Assert.assertEquals("Urlaub: ", fileName.folderPrefix)
        org.junit.Assert.assertEquals("Strand.png", fileName.editableFilename)
        org.junit.Assert.assertEquals("Strand.png", ZaintDocumentStorage.filename)
    }

    @Test
    fun saveAsWithoutSelectedFolderUsesPicturesDefault() {
        presenter.saveAsClicked()

        Mockito.verify(navigator).showSaveImageInformationDialogWhenStandalone(
            PERMISSION_EXTERNAL_STORAGE_SAVE,
            preferences.nextImageNumber,
            false
        )
    }
}
