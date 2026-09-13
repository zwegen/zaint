/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintMirrorDirection
import de.zwegen.zpaint.command.implementation.LayerRotateCommand
import de.zwegen.zpaint.command.implementation.ZaintRotationDirection
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.RotateToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RotateTool(
    private val rotateToolOptionsView: RotateToolOptionsView,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline
) : ZaintToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {
    override val toolType: ZaintToolKind = ZaintToolKind.ROTATE
    override var drawTime: Long = 0L
    private var pendingAngle = DEFAULT_ANGLE
    private var previewLayer: ZaintLayerContracts.ZaintLayer? = null
    private var previewOriginalBitmap: Bitmap? = null
    private val previewScope = CoroutineScope(Dispatchers.Default)
    private var previewJob: Job? = null
    private var previewGeneration = 0

    init {
        toolOptionsViewController.showCheckmark()
        rotateToolOptionsView.setAngle(DEFAULT_ANGLE)
        rotateToolOptionsView.setCallback(object : RotateToolOptionsView.Callback {
            override fun onAngleChanged(angle: Int) {
                pendingAngle = angle
                updatePreview(angle)
            }

            override fun onStartTracking() {
                startPreview()
            }

            override fun onStopTracking(angle: Int) {
                pendingAngle = angle
                updatePreview(angle)
            }

            override fun rotateCounterClockwiseClicked() {
                applyImmediateCommand {
                    commandFactory.createRotateCommand(ZaintRotationDirection.LEFT, true)
                }
            }

            override fun rotateClockwiseClicked() {
                applyImmediateCommand {
                    commandFactory.createRotateCommand(ZaintRotationDirection.RIGHT, true)
                }
            }

            override fun flipHorizontalClicked() {
                applyImmediateCommand {
                    commandFactory.createFlipCommand(ZaintMirrorDirection.VERTICAL, true)
                }
            }

            override fun flipVerticalClicked() {
                applyImmediateCommand {
                    commandFactory.createFlipCommand(ZaintMirrorDirection.HORIZONTAL, true)
                }
            }
        })
    }

    override fun draw(canvas: Canvas) = Unit

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    fun commitPendingRotation(): Boolean {
        cancelPendingPreview()
        val angle = pendingAngle
        if (angle == DEFAULT_ANGLE) {
            restorePreview(resetAngle = false)
            return false
        }
        val originalBitmap = previewOriginalBitmap
        val layer = previewLayer
        if (originalBitmap != null && layer != null) {
            layer.bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        previewOriginalBitmap = null
        previewLayer = null
        pendingAngle = DEFAULT_ANGLE
        rotateToolOptionsView.setAngle(DEFAULT_ANGLE)
        commandManager.addCommand(LayerRotateCommand(angle.toFloat()))
        workspace.invalidate()
        return true
    }

    fun finishPendingRotationOnNavigation() {
        restorePreview(resetAngle = true)
    }

    private fun startPreview() {
        if (previewOriginalBitmap != null) {
            return
        }
        val layer = workspace.layerModel.currentLayer ?: return
        previewLayer = layer
        previewOriginalBitmap = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun updatePreview(angle: Int) {
        val originalBitmap = previewOriginalBitmap ?: return
        val layer = previewLayer ?: return
        val generation = ++previewGeneration
        previewJob?.cancel()
        previewJob = previewScope.launch {
            var result: Bitmap? = null
            try {
                result = if (angle == DEFAULT_ANGLE) {
                    originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    LayerRotateCommand.rotateBitmap(originalBitmap, angle.toFloat())
                }
                withContext(Dispatchers.Main) {
                    val previewStillCurrent = generation == previewGeneration &&
                        previewOriginalBitmap != null &&
                        previewLayer === layer
                    if (previewStillCurrent) {
                        layer.bitmap = result ?: return@withContext
                        result = null
                        workspace.invalidate()
                    }
                }
            } finally {
                result?.recycle()
            }
        }
    }

    private fun restorePreview(resetAngle: Boolean) {
        cancelPendingPreview()
        val originalBitmap = previewOriginalBitmap ?: return
        val layer = previewLayer ?: return
        layer.bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        previewOriginalBitmap = null
        previewLayer = null
        if (resetAngle) {
            pendingAngle = DEFAULT_ANGLE
            rotateToolOptionsView.setAngle(DEFAULT_ANGLE)
        }
        workspace.invalidate()
    }

    private fun applyImmediateCommand(commandProvider: () -> de.zwegen.zpaint.command.Command) {
        restorePreview(resetAngle = true)
        commandManager.addCommand(commandProvider())
        workspace.invalidate()
    }

    private fun cancelPendingPreview() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
    }

    companion object {
        private const val DEFAULT_ANGLE = 0
    }
}
