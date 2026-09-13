package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.graphics.Point
import android.graphics.PointF
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandFactory
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.Tool.StateChange
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController

abstract class ZaintToolBase(
    open var contextCallback: ContextCallback,
    @JvmField var toolOptionsViewController: ZaintToolOptionsController,
    @JvmField
    protected var toolPaint: ToolPaint,
    @JvmField
    protected var workspace: Workspace,
    @JvmField
    protected var idlingResource: CountingIdlingResource,
    @JvmField
    protected var commandManager: ZaintCommandTimeline
) : Tool {
    private val paintControls = ToolPaintControls { toolPaint }
    protected val autoScroll = AutoScrollResolver(contextCallback.scrollTolerance)

    @JvmField
    protected val movedDistance: PointF

    @JvmField
    var previousEventCoordinate: PointF?

    @JvmField
    protected var commandFactory: ZaintCommandFactoryApi = ZaintCommandFactory()

    init {
        movedDistance = PointF(0f, 0f)
        previousEventCoordinate = PointF(0f, 0f)
        if (toolPaint.paint.pathEffect != null) {
            paintControls.removePathEffect()
        }
    }

    override fun onSaveInstanceState(bundle: Bundle?) = Unit

    override fun onRestoreInstanceState(bundle: Bundle?) = Unit

    override fun changePaintColor(@ColorInt color: Int, invalidate: Boolean) {
        paintControls.setColor(color)
    }

    override fun changePaintStrokeWidth(strokeWidth: Int) {
        paintControls.setStrokeWidth(strokeWidth)
    }

    override fun changePaintStrokeCap(cap: Cap) {
        paintControls.setStrokeCap(cap)
    }

    override fun handleDown(coordinate: PointF?): Boolean = true

    override fun handleUp(coordinate: PointF?): Boolean = true

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean = true
    override val drawPaint
        get() = paintControls.drawingPaint()

    abstract override fun draw(canvas: Canvas)

    protected open fun resetInternalState() {}

    override fun resetInternalState(stateChange: StateChange) {
        if (toolType.shouldReactToStateChange(stateChange)) {
            resetInternalState()
        }
    }

    override fun getAutoScrollDirection(
        pointX: Float,
        pointY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Point = autoScroll.resolve(pointX, pointY, screenWidth, screenHeight).let { step ->
        Point(step.horizontal, step.vertical)
    }

    override fun handToolMode(): Boolean = false
}
