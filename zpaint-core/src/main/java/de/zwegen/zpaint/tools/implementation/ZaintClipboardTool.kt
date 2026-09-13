package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.command.implementation.ZaintSelectionClear
import de.zwegen.zpaint.command.implementation.ZaintSelectionShape
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintClipboardOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.roundToInt

private const val BUNDLE_TOOL_READY_FOR_PASTE = "BUNDLE_TOOL_READY_FOR_PASTE"
private const val BUNDLE_TOOL_DRAWING_BITMAP = "BUNDLE_TOOL_DRAWING_BITMAP"

open class ZaintClipboardTool(
    clipboardToolOptionsView: ZaintClipboardOptions,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintRectangleToolBase(
    contextCallback, toolOptionsViewController, toolPaint, workspace, idlingResource, commandManager
), ZaintRectangleToolBase.ShapeSizeChangedListener {
    protected val clipboardToolOptionsView: ZaintClipboardOptions
    protected var readyForPaste = false
    protected val isDrawingBitmapReusable: Boolean
        get() {
            drawingBitmap?.let {
                return it.width == boxWidth.toInt() && it.height == boxHeight.toInt()
            }
            return false
        }

    override open val toolType: ZaintToolKind
        get() = ZaintToolKind.CLIPBOARD

    override fun handleUpAnimations(coordinate: PointF?) {
        super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    init {
        rotationEnabled = true
        this.clipboardToolOptionsView = clipboardToolOptionsView
        setBitmap(Bitmap.createBitmap(boxWidth.toInt(), boxHeight.toInt(), Bitmap.Config.ARGB_8888))
        val callback: ZaintClipboardOptions.Callback = object : ZaintClipboardOptions.Callback {
            override fun copyClicked() {
                highlightBox()
                copyBoxContent()
                this@ZaintClipboardTool.clipboardToolOptionsView.enablePaste(true)
            }

            override fun cutClicked() {
                highlightBox()
                copyBoxContent()
                cutBoxContent()
                this@ZaintClipboardTool.clipboardToolOptionsView.enablePaste(true)
            }

            override fun pasteClicked() {
                highlightBox()
                pasteBoxContent()
            }

            override fun deleteClicked() {
                highlightBox()
                deleteSelectionContent()
            }
        }
        clipboardToolOptionsView.setCallback(callback)
        toolOptionsViewController.showDelayed()
        setShapeSizeChangedListener(this)
        createAndSetShapeSizeText(boxWidth, boxHeight)
    }

    open fun copyBoxContent() {
        if (isDrawingBitmapReusable) {
            drawingBitmap?.eraseColor(Color.TRANSPARENT)
        } else {
            drawingBitmap =
                Bitmap.createBitmap(boxWidth.toInt(), boxHeight.toInt(), Bitmap.Config.ARGB_8888)
        }
        val layerBitmap = workspace.bitmapOfCurrentLayer
        if (layerBitmap != null) {
            drawingBitmap?.let {
                Canvas(it).apply {
                    clipSelectionShape()
                    translate(-toolPosition.x + boxWidth / 2, -toolPosition.y + boxHeight / 2)
                    rotate(-boxRotation, toolPosition.x, toolPosition.y)
                    drawBitmap(layerBitmap, 0f, 0f, null)
                }
            }
        }
        readyForPaste = true
    }

    protected open fun pasteBoxContent(onNewLayer: Boolean = false) {
        drawingBitmap?.let {
            val command = commandFactory.createClipboardCommand(
                it,
                toolPosition,
                boxWidth,
                boxHeight,
                boxRotation
            )
            commandManager.addCommand(
                if (onNewLayer) {
                    ZaintCommandBatch().apply {
                        addCommand(commandFactory.createAddEmptyLayerCommand())
                        addCommand(command)
                    }
                } else {
                    command
                }
            )
        }
    }

    protected open fun cutBoxContent() {
        val command = if (floatingBoxShape == FloatingBoxShape.OVAL) {
            ZaintSelectionClear(
                Point(toolPosition.x.roundToInt(), toolPosition.y.roundToInt()),
                boxWidth,
                boxHeight,
                boxRotation,
                ZaintSelectionShape.OVAL
            )
        } else {
            commandFactory.createCutCommand(toolPosition, boxWidth, boxHeight, boxRotation)
        }
        commandManager.addCommand(command)
    }

    protected open fun deleteSelectionContent() {
        cutBoxContent()
    }

    private fun Canvas.clipSelectionShape() {
        if (floatingBoxShape == FloatingBoxShape.OVAL) {
            val ovalPath = Path().apply {
                addOval(RectF(0f, 0f, boxWidth, boxHeight), Path.Direction.CW)
            }
            clipPath(ovalPath)
        }
    }

    override fun onClickOnButton() {
        if (!readyForPaste || drawingBitmap == null) {
            contextCallback.showNotification(R.string.clipboard_tool_copy_hint)
        } else if (boxIntersectsWorkspace()) {
            pasteBoxContent()
            highlightBox()
        }
    }

    override fun resetInternalState() = Unit
    override fun onSaveInstanceState(bundle: Bundle?) {
        super.onSaveInstanceState(bundle)
        bundle?.putParcelable(BUNDLE_TOOL_DRAWING_BITMAP, drawingBitmap)
        bundle?.putBoolean(BUNDLE_TOOL_READY_FOR_PASTE, readyForPaste)
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        bundle?.apply {
            readyForPaste = getBoolean(BUNDLE_TOOL_READY_FOR_PASTE, readyForPaste)
            drawingBitmap = getParcelable(BUNDLE_TOOL_DRAWING_BITMAP)
        }
        clipboardToolOptionsView.enablePaste(readyForPaste)
    }
    override fun onShapeSizeChanged(shapeText: String) {
        clipboardToolOptionsView.setShapeSizeText(shapeText)
    }

    override fun onToggleVisibility(isVisible: Boolean) {
        clipboardToolOptionsView.toggleShapeSizeVisibility(isVisible)
    }

    fun changeClipboardToolLayoutVisibility(willHide: Boolean, disabled: Boolean = false) {
        changeToolLayoutVisibility(clipboardToolOptionsView.getClipboardToolOptionsLayout(), willHide, disabled)
    }
}
