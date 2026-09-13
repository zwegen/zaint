/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.BorderToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController

class BorderTool(
    private val borderToolOptionsView: BorderToolOptionsView,
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
    override val toolType: ZaintToolKind = ZaintToolKind.BORDER
    override var drawTime: Long = 0L
    private var pendingAmount = DEFAULT_AMOUNT

    init {
        toolOptionsViewController.showCheckmark()
        borderToolOptionsView.setBorderAmount(DEFAULT_AMOUNT)
        resetPerspectiveForBorderTool()
        borderToolOptionsView.setCallback(object : BorderToolOptionsView.Callback {
            override fun onBorderAmountChanged(amount: Int) {
                pendingAmount = amount
                updatePreview(amount)
            }

            override fun onStartTracking() {
                updatePreview(pendingAmount)
            }

            override fun onStopTracking(amount: Int) {
                pendingAmount = amount
                updatePreview(amount)
            }
        })
    }

    override fun draw(canvas: Canvas) {
        val amount = effectiveAmount(pendingAmount, workspace.layerModel.width, workspace.layerModel.height)
        if (amount > 0) {
            drawAddedBorderPreview(canvas, amount)
        } else if (amount < 0) {
            drawCropPreview(canvas, -amount)
        }
    }

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun changePaintColor(color: Int, invalidate: Boolean) {
        super.changePaintColor(color, invalidate)
        if (invalidate) {
            workspace.invalidate()
        }
    }

    override fun resetInternalState() {
        finishPendingBorderOnNavigation()
    }

    fun commitPendingBorder(): Boolean {
        val amount = pendingAmount
        if (amount == DEFAULT_AMOUNT) {
            return false
        }
        val maximumBitmapResolution = maximumBitmapResolution()
        val targetDimensions = calculateTargetDimensions(amount)
        val command = commandFactory.createBorderCommand(amount, toolPaint.color, maximumBitmapResolution)
        commandManager.addCommand(command)
        targetDimensions?.let { (targetWidth, targetHeight) ->
            centerPerspectiveOnDimensions(targetWidth, targetHeight)
        }
        pendingAmount = DEFAULT_AMOUNT
        borderToolOptionsView.setBorderAmount(DEFAULT_AMOUNT)
        workspace.invalidate()
        return true
    }

    fun finishPendingBorderOnNavigation() {
        pendingAmount = DEFAULT_AMOUNT
        borderToolOptionsView.setBorderAmount(DEFAULT_AMOUNT)
        workspace.invalidate()
    }

    private fun updatePreview(amount: Int) {
        if (!isValidAmountForPreview(amount)) {
            return
        }
        pendingAmount = amount
        resetPerspectiveForBorderPreview(amount)
        workspace.invalidate()
    }

    private fun drawAddedBorderPreview(canvas: Canvas, amount: Int) {
        val width = workspace.layerModel.width.toFloat()
        val height = workspace.layerModel.height.toFloat()
        val border = amount.toFloat()
        val paint = Paint().apply {
            style = Paint.Style.FILL
            color = toolPaint.color
        }

        canvas.drawRect(-border, -border, width + border, 0f, paint)
        canvas.drawRect(-border, height, width + border, height + border, paint)
        canvas.drawRect(-border, 0f, 0f, height, paint)
        canvas.drawRect(width, 0f, width + border, height, paint)
    }

    private fun drawCropPreview(canvas: Canvas, crop: Int) {
        val width = workspace.layerModel.width.toFloat()
        val height = workspace.layerModel.height.toFloat()
        val inset = crop.toFloat()
        val overlayPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.argb(CROP_OVERLAY_ALPHA, 0, 0, 0)
        }
        val outlinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = CROP_OUTLINE_WIDTH
            color = Color.WHITE
        }

        canvas.drawRect(0f, 0f, width, inset, overlayPaint)
        canvas.drawRect(0f, height - inset, width, height, overlayPaint)
        canvas.drawRect(0f, inset, inset, height - inset, overlayPaint)
        canvas.drawRect(width - inset, inset, width, height - inset, overlayPaint)
        canvas.drawRect(inset, inset, width - inset, height - inset, outlinePaint)
    }

    private fun resetPerspectiveForBorderTool() {
        resetPerspectiveForBorderPreview(DEFAULT_AMOUNT)
        workspace.invalidate()
    }

    /**
     * A positive border is drawn outside the current bitmap while it is still only a preview.
     * Reserve exactly that extra space in the viewport so the preview is never hidden beyond
     * the old bitmap edge.
     */
    private fun resetPerspectiveForBorderPreview(amount: Int) {
        workspace.perspective.setBitmapDimensions(workspace.layerModel.width, workspace.layerModel.height)
        val previewPadding = if (amount > 0) amount * 2 + PREVIEW_EDGE_PADDING else 0
        workspace.perspective.resetScaleAndTranslationWithPadding(previewPadding)
    }

    private fun centerPerspectiveOnCurrentBitmap() {
        centerPerspectiveOnDimensions(workspace.layerModel.width, workspace.layerModel.height)
    }

    private fun centerPerspectiveOnDimensions(width: Int, height: Int) {
        workspace.perspective.setBitmapDimensions(width, height)
        workspace.perspective.centerBitmap()
    }

    private fun calculateTargetDimensions(amount: Int): Pair<Int, Int>? {
        val width = workspace.layerModel.width
        val height = workspace.layerModel.height
        val effectiveAmount = effectiveAmount(amount, width, height)
        if (effectiveAmount == 0) {
            return null
        }

        val targetWidth = width + effectiveAmount * 2
        val targetHeight = height + effectiveAmount * 2
        if (targetWidth < 1 || targetHeight < 1 ||
            targetWidth.toLong() * targetHeight.toLong() > maximumBitmapResolution().toLong()
        ) {
            return null
        }
        return targetWidth to targetHeight
    }

    private fun isValidAmountForPreview(amount: Int): Boolean {
        val targetDimensions = calculateTargetDimensions(amount)
        return amount == DEFAULT_AMOUNT || targetDimensions != null
    }

    private fun effectiveAmount(amount: Int, width: Int, height: Int): Int {
        if (amount >= 0) {
            return amount
        }
        val maximumCrop = ((minOf(width, height) - 1) / 2).coerceAtLeast(0)
        return -((-amount).coerceAtMost(maximumCrop))
    }

    private fun maximumBitmapResolution(
        width: Int = workspace.layerModel.width,
        height: Int = workspace.layerModel.height
    ): Int =
        (width.toLong() * height.toLong() * MAXIMUM_BITMAP_SIZE_FACTOR)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    companion object {
        private const val DEFAULT_AMOUNT = 0
        private const val PREVIEW_EDGE_PADDING = 2
        private const val CROP_OVERLAY_ALPHA = 150
        private const val CROP_OUTLINE_WIDTH = 2f
        private const val MAXIMUM_BITMAP_SIZE_FACTOR = 10L
    }
}
