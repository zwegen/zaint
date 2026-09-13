package de.zwegen.zpaint.tools.implementation

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.annotation.VisibleForTesting
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.max
import kotlin.math.min

private const val BUNDLE_TOOL_POSITION_Y = "TOOL_POSITION_Y"
private const val BUNDLE_TOOL_POSITION_X = "TOOL_POSITION_X"

abstract class ZaintShapeToolBase @SuppressLint("VisibleForTests") constructor(
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

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @JvmField
    var toolPosition: PointF

    @JvmField
    var primaryShapeColor: Int =
        contextCallback.getColor(R.color.zpaint_main_rectangle_tool_primary_color)

    @JvmField
    var secondaryShapeColor: Int = contextCallback.getColor(R.color.zpaint_colorAccentAlpha60)

    @JvmField
    val linePaint: Paint

    @JvmField
    val metrics: DisplayMetrics = contextCallback.displayMetrics

    init {
        val perspective = workspace.perspective
        toolPosition = if (perspective.scale > 1) {
            PointF(
                perspective.surfaceCenterX - perspective.surfaceTranslationX,
                perspective.surfaceCenterY - perspective.surfaceTranslationY
            )
        } else {
            PointF(workspace.width / 2f, workspace.height / 2f)
        }
        linePaint = Paint()
        linePaint.color = primaryShapeColor
        linePaint.pathEffect = null
    }

    abstract fun drawShape(canvas: Canvas)

    fun getStrokeWidthForZoom(
        defaultStrokeWidth: Float,
        minStrokeWidth: Float,
        maxStrokeWidth: Float
    ): Float {
        val strokeWidth = defaultStrokeWidth * metrics.density / workspace.scale
        return min(maxStrokeWidth, max(minStrokeWidth, strokeWidth))
    }

    fun getInverselyProportionalSizeForZoom(defaultSize: Float): Float {
        val applicationScale = workspace.scale
        return defaultSize * metrics.density / applicationScale
    }

    override fun onSaveInstanceState(bundle: Bundle?) {
        super.onSaveInstanceState(bundle)
        bundle?.apply {
            putFloat(BUNDLE_TOOL_POSITION_X, toolPosition.x)
            putFloat(BUNDLE_TOOL_POSITION_Y, toolPosition.y)
        }
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        bundle?.apply {
            toolPosition.x = getFloat(BUNDLE_TOOL_POSITION_X, toolPosition.x)
            toolPosition.y = getFloat(BUNDLE_TOOL_POSITION_Y, toolPosition.y)
        }
    }

    override fun getAutoScrollDirection(
        pointX: Float,
        pointY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Point {
        val surfaceToolPosition = workspace.getSurfacePointFromCanvasPoint(toolPosition)
        return autoScroll.resolve(
            surfaceToolPosition.x,
            surfaceToolPosition.y,
            screenWidth,
            screenHeight
        ).let { step -> Point(step.horizontal, step.vertical) }
    }

    abstract fun onClickOnButton()

    open fun onClickOnNewLayerButton() = onClickOnButton()

    protected fun overlapsVisibleCurrentLayerPixels(
        proposedBounds: RectF,
        drawProposal: (Canvas) -> Unit
    ): Boolean = VisiblePixelOverlap.overlaps(
        workspace.bitmapOfCurrentLayer,
        proposedBounds,
        drawProposal
    )

    protected open fun drawToolSpecifics(canvas: Canvas, boxWidth: Float, boxHeight: Float) {}
}
