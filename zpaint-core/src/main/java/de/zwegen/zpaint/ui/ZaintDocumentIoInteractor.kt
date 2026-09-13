package de.zwegen.zpaint.ui

import android.content.Context
import android.net.Uri
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.serialization.ZaintProjectSerializer
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.contract.ZaintEditorContracts.Interactor
import de.zwegen.zpaint.iotasks.BitmapSize
import de.zwegen.zpaint.iotasks.CreateFile
import de.zwegen.zpaint.iotasks.CreateFile.CreateFileCallback
import de.zwegen.zpaint.iotasks.ZaintDocumentLoadTask
import de.zwegen.zpaint.iotasks.ZaintDocumentLoadTask.LoadImageCallback
import de.zwegen.zpaint.iotasks.ZaintDocumentSaveTask
import de.zwegen.zpaint.iotasks.ZaintDocumentSaveTask.SaveImageCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Starts Zaint's document I/O jobs while keeping the activity-facing API
 * independent from the individual save and load task implementations.
 */
class ZaintDocumentIoInteractor(
    private val idlingResource: CountingIdlingResource,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : Interactor {

    override fun saveCopy(
        callback: SaveImageCallback,
        requestCode: Int,
        layerModel: ZaintLayerContracts.Model,
        commandSerializer: ZaintProjectSerializer,
        uri: Uri?,
        context: Context
    ) = startSave(callback, requestCode, layerModel, uri, true, context, commandSerializer)

    override fun saveCopy(
        callback: SaveImageCallback,
        requestCode: Int,
        layerModel: ZaintLayerContracts.Model,
        uri: Uri?,
        context: Context
    ) = startSave(callback, requestCode, layerModel, uri, true, context, null)

    override fun createFile(callback: CreateFileCallback, requestCode: Int, filename: String) {
        CreateFile(callback, requestCode, filename, ioScope).execute()
    }

    override fun saveImage(
        callback: SaveImageCallback,
        requestCode: Int,
        layerModel: ZaintLayerContracts.Model,
        commandSerializer: ZaintProjectSerializer,
        uri: Uri?,
        context: Context
    ) = startSave(callback, requestCode, layerModel, uri, false, context, commandSerializer)

    override fun saveImage(
        callback: SaveImageCallback,
        requestCode: Int,
        layerModel: ZaintLayerContracts.Model,
        uri: Uri?,
        context: Context
    ) = startSave(callback, requestCode, layerModel, uri, false, context, null)

    override fun loadFile(
        callback: LoadImageCallback,
        requestCode: Int,
        uri: Uri?,
        context: Context,
        scaling: Boolean,
        commandSerializer: ZaintProjectSerializer,
        visibleDrawingSurfaceSize: BitmapSize?
    ) {
        ZaintDocumentLoadTask(
            callback = callback,
            requestCode = requestCode,
            uri = uri,
            context = context,
            scaleImage = scaling,
            commandSerializer = commandSerializer,
            visibleDrawingSurfaceSize = visibleDrawingSurfaceSize,
            scopeIO = ioScope,
            idlingResource = idlingResource
        ).execute()
    }

    private fun startSave(
        callback: SaveImageCallback,
        requestCode: Int,
        layerModel: ZaintLayerContracts.Model,
        uri: Uri?,
        createCopy: Boolean,
        context: Context,
        serializer: ZaintProjectSerializer?
    ) {
        ZaintDocumentSaveTask(
            activity = callback,
            requestCode = requestCode,
            layerModel = layerModel,
            uri = uri,
            saveAsCopy = createCopy,
            context = context,
            commandSerializer = serializer,
            scopeIO = ioScope,
            idlingResource = idlingResource
        ).execute()
    }
}
