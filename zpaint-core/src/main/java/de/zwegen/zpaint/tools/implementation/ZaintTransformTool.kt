package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.VisibleForTesting
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintMirrorDirection
import de.zwegen.zpaint.command.implementation.ZaintRotationDirection
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.helper.DefaultNumberRangeFilter
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.tools.options.ToolOptionsVisibilityController
import de.zwegen.zpaint.tools.options.ZaintTransformOptions
import de.zwegen.zpaint.tools.options.ZaintTransformOptions.AspectRatio
import de.zwegen.zpaint.ui.tools.NumberRangeFilter
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@VisibleForTesting
const val MAXIMUM_BITMAP_SIZE_FACTOR = 4.0f
private const val START_ZOOM_FACTOR = 0.95f
private const val SIDES = 4
private const val CONSTANT_1 = 10
private const val RIGHT_ANGLE = 90f
private const val HUNDRED = 100f
private const val ROTATION_ENABLED = false
private const val RESIZE_POINTS_VISIBLE = false
private const val RESPECT_MAXIMUM_BORDER_RATIO = false
private const val RESPECT_MAXIMUM_BOX_RESOLUTION = true

class ZaintTransformTool(
    private val transformOptions: ZaintTransformOptions,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintRectangleToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {
    @VisibleForTesting
    @JvmField
    var resizeBoundWidthXLeft = 0f

    @VisibleForTesting
    @JvmField
    var resizeBoundWidthXRight = 0f

    @VisibleForTesting
    @JvmField
    var resizeBoundHeightYTop = 0f

    @VisibleForTesting
    @JvmField
    var resizeBoundHeightYBottom = 0f

    var checkMarkClicked = false

    private var cropRunFinished = false
    private var maxImageResolutionInformationAlreadyShown = false
    private var zeroSizeBitmap = false
    private var transformCurrentLayerOnly = true
    private var aspectRatioPreset = AspectRatio.FREE
    private var resizePercentage = 100
    private var lastAppliedCropBounds: CropBounds? = null
    private val rangeFilterHeight: NumberRangeFilter
    private val rangeFilterWidth: NumberRangeFilter

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.TRANSFORM

    override fun handleUpAnimations(coordinate: PointF?) {
        super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    init {
        rotationEnabled = ROTATION_ENABLED
        resizePointsVisible = RESIZE_POINTS_VISIBLE
        respectMaximumBorderRatio = RESPECT_MAXIMUM_BORDER_RATIO
        boxHeight = workspace.height.toFloat()
        boxWidth = workspace.width.toFloat()
        toolPosition.x = boxWidth / 2f
        toolPosition.y = boxHeight / 2f
        cropRunFinished = true
        maximumBoxResolution =
            metrics.widthPixels * metrics.heightPixels * MAXIMUM_BITMAP_SIZE_FACTOR
        respectMaximumBoxResolution = RESPECT_MAXIMUM_BOX_RESOLUTION
        initResizeBounds()
        toolOptionsViewController.setCallback(object : ToolOptionsVisibilityController.Callback {
            override fun onHide() {
                if (!zeroSizeBitmap) {
                    contextCallback.showNotificationWithDuration(
                        R.string.transform_info_text,
                        ContextCallback.NotificationDuration.LONG
                    )
                } else {
                    zeroSizeBitmap = false
                }
            }

            override fun onShow() {
                updateToolOptions()
            }
        })
        transformOptions.setListener(object : ZaintTransformOptions.Listener {
            override fun onRotateLeft() {
                rotateCounterClockWise()
            }

            override fun onRotateRight() {
                rotateClockWise()
            }

            override fun onFlipHorizontal() {
                flipHorizontal()
            }

            override fun onFlipVertical() {
                flipVertical()
            }

            override fun onWidthChanged(width: Float) {
                this@ZaintTransformTool.boxWidth = width
                applyAspectRatioToBox(preferWidth = true)
                updateToolOptions()
            }

            override fun onHeightChanged(height: Float) {
                this@ZaintTransformTool.boxHeight = height
                applyAspectRatioToBox(preferWidth = false)
                updateToolOptions()
            }

            override fun onCurrentLayerOnlyChanged(currentLayerOnly: Boolean) {
                transformCurrentLayerOnly = currentLayerOnly
            }

            override fun onAspectRatioChanged(aspectRatio: AspectRatio) {
                this@ZaintTransformTool.aspectRatioPreset = aspectRatio
                if (aspectRatio == AspectRatio.FREE) {
                    resetCropToWorkspaceBounds()
                } else {
                    fitMaximumAspectRatioBoxInsideWorkspace()
                }
                updateToolOptions()
                workspace.invalidate()
            }

            override fun onHideRequested() {
                this@ZaintTransformTool.toolOptionsViewController.hide()
            }

            override fun onApplyResize(percent: Int) {
                onApplyResizeClicked(percent)
            }

            override fun onResizePercentChanged(percent: Int) {
                this@ZaintTransformTool.resizePercentage = percent
            }
        })
        rangeFilterHeight = DefaultNumberRangeFilter(1, (maximumBoxResolution / boxWidth).toInt())
        rangeFilterWidth = DefaultNumberRangeFilter(1, (maximumBoxResolution / boxHeight).toInt())
        transformOptions.setHeightRange(rangeFilterHeight)
        transformOptions.setWidthRange(rangeFilterWidth)
        updateToolOptions()
        toolOptionsViewController.showDelayed()
    }

    override fun resetInternalState() {
        initialiseResizingState()
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        return super.handleDown(coordinate)
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        val oldWidth = boxWidth
        val oldHeight = boxHeight
        val handled = super.handleMove(coordinate, true)
        if (handled && aspectRatioPreset != AspectRatio.FREE) {
            val widthChangedMore = kotlin.math.abs(boxWidth - oldWidth) >= kotlin.math.abs(boxHeight - oldHeight)
            applyAspectRatioToBox(preferWidth = widthChangedMore)
            updateToolOptions()
        }
        return handled
    }

    override fun drawToolSpecifics(canvas: Canvas, boxWidth: Float, boxHeight: Float) {

        var width = boxWidth
        var height = boxHeight

        if (cropRunFinished) {
            linePaint.color = primaryShapeColor
            linePaint.strokeWidth = toolStrokeWidth * 2

            val rightTopPoint = PointF(-width / 2, -height / 2)
            repeat(SIDES) {
                val resizeLineLengthHeight = height / CONSTANT_1
                val resizeLineLengthWidth = width / CONSTANT_1
                canvas.drawLine(
                    rightTopPoint.x - toolStrokeWidth / 2,
                    rightTopPoint.y,
                    rightTopPoint.x + resizeLineLengthWidth,
                    rightTopPoint.y,
                    linePaint
                )
                canvas.drawLine(
                    rightTopPoint.x,
                    rightTopPoint.y - toolStrokeWidth / 2,
                    rightTopPoint.x,
                    rightTopPoint.y + resizeLineLengthHeight,
                    linePaint
                )
                canvas.drawLine(
                    rightTopPoint.x + width / 2 - resizeLineLengthWidth,
                    rightTopPoint.y,
                    rightTopPoint.x + width / 2 + resizeLineLengthWidth,
                    rightTopPoint.y,
                    linePaint
                )
                canvas.rotate(RIGHT_ANGLE)
                val tempX = rightTopPoint.x
                rightTopPoint.x = rightTopPoint.y
                rightTopPoint.y = tempX
                val tempHeight = height
                height = width
                width = tempHeight
            }
        }
    }

    private fun resetScaleAndTranslation() {
        workspace.resetPerspective()
        val zoomFactor = workspace.scaleForCenterBitmap * START_ZOOM_FACTOR
        workspace.scale = zoomFactor
    }

    private fun initialiseResizingState() {
        cropRunFinished = false
        resizeBoundWidthXRight = 0f
        resizeBoundHeightYBottom = 0f
        resizeBoundWidthXLeft = workspace.width.toFloat()
        resizeBoundHeightYTop = workspace.height.toFloat()
        if (checkMarkClicked) {
            val cropBounds = lastAppliedCropBounds
            if (cropBounds != null) {
                resizeBoundWidthXLeft = cropBounds.left.toFloat()
                resizeBoundHeightYTop = cropBounds.top.toFloat()
                resizeBoundWidthXRight = cropBounds.right.toFloat()
                resizeBoundHeightYBottom = cropBounds.bottom.toFloat()
            } else {
                resizeBoundWidthXLeft = toolPosition.x - workspace.width / 2f
                resizeBoundHeightYTop = toolPosition.y - workspace.height / 2f
                resizeBoundWidthXRight = resizeBoundWidthXLeft + workspace.width - 1f
                resizeBoundHeightYBottom = resizeBoundHeightYTop + workspace.height - 1f
            }
        } else {
            resetScaleAndTranslation()
            resizeBoundWidthXRight = workspace.width - 1f
            resizeBoundHeightYBottom = workspace.height - 1f
            resizeBoundWidthXLeft = 0f
            resizeBoundHeightYTop = 0f
        }

        setRectangle(
            RectF(
                resizeBoundWidthXLeft,
                resizeBoundHeightYTop,
                resizeBoundWidthXRight,
                resizeBoundHeightYBottom
            )
        )
        if (checkMarkClicked) {
            workspace.perspective.surfaceTranslationX += resizeBoundWidthXLeft
            workspace.perspective.surfaceTranslationY += resizeBoundHeightYTop
            workspace.perspective.setBitmapDimensions(boxWidth.toInt(), boxHeight.toInt())
            toolPosition.x -= resizeBoundWidthXLeft
            toolPosition.y -= resizeBoundHeightYTop
            lastAppliedCropBounds = null
            checkMarkClicked = false
        }
        cropRunFinished = true
        updateToolOptions()
    }

    private fun executeResizeCommand() {
        if (cropRunFinished) {
            cropRunFinished = false
            initResizeBounds()
            val cropBounds = createCropBounds()
            if (areResizeBordersValid(cropBounds)) {
                lastAppliedCropBounds = cropBounds
                val resizeCommand = commandFactory.createCropCommand(
                    cropBounds.left,
                    cropBounds.top,
                    cropBounds.right,
                    cropBounds.bottom,
                    maximumBoxResolution.toInt()
                )
                commandManager.addCommand(resizeCommand)
            } else {
                cropRunFinished = true
                contextCallback.showNotification(R.string.resize_nothing_to_resize)
            }
        }
    }

    private fun onApplyResizeClicked(resizePercentage: Int) {
        val newWidth = (workspace.width / HUNDRED * resizePercentage).toInt()
        val newHeight = (workspace.height / HUNDRED * resizePercentage).toInt()
        if (newWidth == 0 || newHeight == 0) {
            zeroSizeBitmap = true
            contextCallback.showNotificationWithDuration(
                R.string.resize_cannot_resize_to_this_size,
                ContextCallback.NotificationDuration.LONG
            )
        } else {
            val command = commandFactory.createResizeCommand(newWidth, newHeight)
            commandManager.addCommand(command)
        }
    }

    private fun flipHorizontal() {
        val command = commandFactory.createFlipCommand(
            ZaintMirrorDirection.HORIZONTAL,
            transformCurrentLayerOnly
        )
        commandManager.addCommand(command)
    }

    private fun flipVertical() {
        val command = commandFactory.createFlipCommand(
            ZaintMirrorDirection.VERTICAL,
            transformCurrentLayerOnly
        )
        commandManager.addCommand(command)
    }

    private fun rotateCounterClockWise() {
        val command = commandFactory.createRotateCommand(
            ZaintRotationDirection.LEFT,
            transformCurrentLayerOnly
        )
        commandManager.addCommand(command)
        if (!transformCurrentLayerOnly) {
            swapWidthAndHeight()
        }
    }

    private fun rotateClockWise() {
        val command = commandFactory.createRotateCommand(
            ZaintRotationDirection.RIGHT,
            transformCurrentLayerOnly
        )
        commandManager.addCommand(command)
        if (!transformCurrentLayerOnly) {
            swapWidthAndHeight()
        }
    }

    private fun swapWidthAndHeight() {
        val tempBoxWidth = boxWidth
        boxWidth = boxHeight
        boxHeight = tempBoxWidth
    }

    private fun createCropBounds(): CropBounds {
        val left = floor(resizeBoundWidthXLeft).toInt()
        val top = floor(resizeBoundHeightYTop).toInt()
        val width = max(1, boxWidth.toInt())
        val height = max(1, boxHeight.toInt())
        return CropBounds(
            left = left,
            top = top,
            right = left + width - 1,
            bottom = top + height - 1
        )
    }

    private fun areResizeBordersValid(cropBounds: CropBounds): Boolean {
        if (cropBounds.right < cropBounds.left || cropBounds.top > cropBounds.bottom) {
            return false
        }
        if (cropBounds.left >= workspace.width ||
            min(cropBounds.right, cropBounds.bottom) < 0 ||
            cropBounds.top >= workspace.height
        ) {
            return false
        }
        if (cropBounds.left == 0 &&
            cropBounds.top == 0 &&
            cropBounds.right == workspace.width - 1 &&
            cropBounds.bottom == workspace.height - 1
        ) {
            return false
        }
        val width = cropBounds.right - cropBounds.left + 1
        val height = cropBounds.bottom - cropBounds.top + 1
        return width.toLong() * height.toLong() <= maximumBoxResolution
    }

    private fun setRectangle(rectangle: RectF) {
        boxWidth = rectangle.right - rectangle.left + 1f
        boxHeight = rectangle.bottom - rectangle.top + 1f
        if (!checkMarkClicked) {
            toolPosition.x = rectangle.left + boxWidth / 2f
            toolPosition.y = rectangle.top + boxHeight / 2f
        }
    }

    private fun initResizeBounds() {
        resizeBoundWidthXLeft = toolPosition.x - boxWidth / 2f
        resizeBoundWidthXRight = toolPosition.x + boxWidth / 2f - 1f
        resizeBoundHeightYTop = toolPosition.y - boxHeight / 2f
        resizeBoundHeightYBottom = toolPosition.y + boxHeight / 2f - 1f
    }

    private fun applyAspectRatioToBox(preferWidth: Boolean) {
        if (aspectRatioPreset == AspectRatio.FREE) {
            return
        }
        val ratio = aspectRatioPreset.width.toFloat() / aspectRatioPreset.height.toFloat()
        if (preferWidth) {
            boxHeight = boxWidth / ratio
        } else {
            boxWidth = boxHeight * ratio
        }
        if (respectMaximumBoxResolution && maximumBoxResolution > 0 && boxWidth * boxHeight > maximumBoxResolution) {
            val scale = sqrt(maximumBoxResolution / (boxWidth * boxHeight))
            boxWidth *= scale
            boxHeight *= scale
        }
        fitBoxInsideWorkspace()
    }

    private fun resetCropToWorkspaceBounds() {
        boxWidth = workspace.width.toFloat()
        boxHeight = workspace.height.toFloat()
        toolPosition.x = boxWidth / 2f
        toolPosition.y = boxHeight / 2f
    }

    private fun fitMaximumAspectRatioBoxInsideWorkspace() {
        val workspaceWidth = workspace.width.toFloat()
        val workspaceHeight = workspace.height.toFloat()
        if (workspaceWidth <= 0f || workspaceHeight <= 0f) {
            return
        }

        val ratio = aspectRatioPreset.width.toFloat() / aspectRatioPreset.height.toFloat()
        if (workspaceWidth / workspaceHeight > ratio) {
            boxHeight = workspaceHeight
            boxWidth = boxHeight * ratio
        } else {
            boxWidth = workspaceWidth
            boxHeight = boxWidth / ratio
        }

        if (respectMaximumBoxResolution && maximumBoxResolution > 0 && boxWidth * boxHeight > maximumBoxResolution) {
            val scale = sqrt(maximumBoxResolution / (boxWidth * boxHeight))
            boxWidth *= scale
            boxHeight *= scale
        }

        toolPosition.x = workspaceWidth / 2f
        toolPosition.y = workspaceHeight / 2f
    }

    private fun fitBoxInsideWorkspace() {
        val workspaceWidth = workspace.width.toFloat()
        val workspaceHeight = workspace.height.toFloat()
        if (workspaceWidth <= 0f || workspaceHeight <= 0f) {
            return
        }

        val scale = min(
            1f,
            min(workspaceWidth / boxWidth, workspaceHeight / boxHeight)
        )
        boxWidth *= scale
        boxHeight *= scale

        val halfWidth = boxWidth / 2f
        val halfHeight = boxHeight / 2f
        toolPosition.x = toolPosition.x.coerceIn(halfWidth, workspaceWidth - halfWidth)
        toolPosition.y = toolPosition.y.coerceIn(halfHeight, workspaceHeight - halfHeight)
    }

    override fun onClickOnButton() {
        if (resizePercentage != 100) {
            onApplyResizeClicked(resizePercentage)
            resizePercentage = 100
            transformOptions.showResizePercent(resizePercentage)
            checkMarkClicked = false
        } else {
            executeResizeCommand()
        }
    }

    override fun preventThatBoxGetsTooLarge(
        oldWidth: Float,
        oldHeight: Float,
        oldPosX: Float,
        oldPosY: Float
    ) {
        super.preventThatBoxGetsTooLarge(oldWidth, oldHeight, oldPosX, oldPosY)
        if (!maxImageResolutionInformationAlreadyShown) {
            contextCallback.showNotification(R.string.resize_max_image_resolution_reached)
            maxImageResolutionInformationAlreadyShown = true
        }
    }

    private fun updateToolOptions() {
        rangeFilterHeight.max = (maximumBoxResolution / boxWidth).toInt()
        rangeFilterWidth.max = (maximumBoxResolution / boxHeight).toInt()
        transformOptions.showWidth(boxWidth.toInt())
        transformOptions.showHeight(boxHeight.toInt())
    }

    private data class CropBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
