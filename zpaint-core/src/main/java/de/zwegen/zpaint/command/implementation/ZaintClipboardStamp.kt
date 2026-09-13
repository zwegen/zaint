package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import androidx.annotation.VisibleForTesting
import de.zwegen.zpaint.ZPaintApplication
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.clipboard.AndroidClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardBitmapStorage
import de.zwegen.zpaint.command.clipboard.ClipboardPlacement
import de.zwegen.zpaint.command.clipboard.ClipboardUndoSnapshot
import de.zwegen.zpaint.contract.ZaintLayerContracts
import java.io.File

/** Places a snapshot of clipboard pixels and retains a file-backed undo source after first use. */
class ZaintClipboardStamp(
    bitmap: Bitmap,
    position: Point,
    width: Float,
    height: Float,
    rotation: Float,
    private val bitmapStorage: ClipboardBitmapStorage = AndroidClipboardBitmapStorage { ZPaintApplication.cacheDir }
) : Command {
    private val request = StampRequest(Point(position.x, position.y), width, height, rotation)
    private val undoSnapshot = ClipboardUndoSnapshot(bitmap, bitmapStorage)

    val coordinates: Point
        get() = Point(request.center.x, request.center.y)

    val boxWidth: Float
        get() = request.width

    val boxHeight: Float
        get() = request.height

    val boxRotation: Float
        get() = request.rotation

    val bitmap: Bitmap?
        get() = undoSnapshot.inMemoryBitmap

    var fileToStoredBitmap: File?
        get() = undoSnapshot.storedFile
        set(value) {
            undoSnapshot.storedFile = value
        }

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        val bitmapToDraw = undoSnapshot.bitmapForDrawing() ?: return
        request.placement.drawOn(canvas, bitmapToDraw)
        undoSnapshot.persist(bitmapToDraw, request.width, request.height)
        undoSnapshot.release(bitmapToDraw)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun storeBitmap(bitmapToStore: Bitmap, boxWidth: Float, boxHeight: Float) {
        undoSnapshot.persist(bitmapToStore, boxWidth, boxHeight)
    }

    override fun freeResources() {
        undoSnapshot.free()
    }

    private data class StampRequest(
        val center: Point,
        val width: Float,
        val height: Float,
        val rotation: Float
    ) {
        val placement: ClipboardPlacement
            get() = ClipboardPlacement(center, width, height, rotation)
    }
}
