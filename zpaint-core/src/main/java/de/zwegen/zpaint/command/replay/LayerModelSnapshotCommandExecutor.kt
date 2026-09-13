package de.zwegen.zpaint.command.replay

import android.graphics.Bitmap
import android.graphics.Canvas
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.implementation.ZaintAddLayerCommand
import de.zwegen.zpaint.command.implementation.ZaintLayerOpacityChange
import de.zwegen.zpaint.command.implementation.ZaintPointCommand
import de.zwegen.zpaint.command.implementation.ZaintStrokeCommand
import de.zwegen.zpaint.command.implementation.ZaintRemoveLayerCommand
import de.zwegen.zpaint.command.implementation.ZaintLayerReorder
import de.zwegen.zpaint.command.implementation.ZaintSelectLayerCommand
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.model.LayerModelSnapshot
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Executes an explicitly supported command sequence on a model restored from [LayerModelSnapshot].
 *
 * This boundary never reads from or writes to the UI model. [execute] restores private bitmap copies
 * into a temporary [ZaintLayerModel], captures a new immutable snapshot, then recycles every bitmap owned
 * by that temporary model on both success and failure. The supplied start snapshot remains caller-
 * owned and is neither released nor recycled here; the returned snapshot is caller-owned.
 */
class LayerModelSnapshotCommandExecutor(private val commonFactory: CommonFactory) {
    fun execute(startSnapshot: LayerModelSnapshot, commands: List<Command>): LayerModelSnapshot {
        commands.forEach(SnapshotReplayCommandPolicy::requireSupported)

        val temporaryModel = ZaintLayerModel()
        val temporaryBitmaps: MutableSet<Bitmap> = Collections.newSetFromMap(IdentityHashMap())
        var canvas: Canvas? = null
        try {
            startSnapshot.restoreInto(temporaryModel)
            trackModelBitmaps(temporaryModel, temporaryBitmaps)

            canvas = commonFactory.createCanvas()
            commands.forEach { command ->
                canvas.setBitmap(temporaryModel.currentLayer?.bitmap)
                command.run(canvas, temporaryModel)
                trackModelBitmaps(temporaryModel, temporaryBitmaps)
            }

            return LayerModelSnapshot.capture(temporaryModel)
        } finally {
            canvas?.setBitmap(null)
            temporaryModel.currentLayer = null
            temporaryModel.reset()
            temporaryBitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun trackModelBitmaps(model: ZaintLayerModel, temporaryBitmaps: MutableSet<Bitmap>) {
        model.layers.forEach { layer -> temporaryBitmaps.add(layer.bitmap) }
    }
}

/**
 * Conservative allow-list for commands whose execution depends only on the supplied canvas and
 * [de.zwegen.zpaint.contract.ZaintLayerContracts.Model]. Commands with tool/UI state, external data, or
 * hidden bitmap ownership remain rejected until they gain an explicit replay contract.
 */
object SnapshotReplayCommandPolicy {
    private val supportedCommandClasses = setOf(
        ZaintAddLayerCommand::class.java,
        ZaintLayerOpacityChange::class.java,
        ZaintStrokeCommand::class.java,
        ZaintPointCommand::class.java,
        ZaintRemoveLayerCommand::class.java,
        ZaintLayerReorder::class.java,
        ZaintSelectLayerCommand::class.java
    )

    fun supports(commandClass: Class<out Command>): Boolean = commandClass in supportedCommandClasses

    fun requireSupported(command: Command) {
        require(supports(command.javaClass)) {
            "Command ${command.javaClass.name} is not supported for isolated snapshot replay"
        }
    }
}
