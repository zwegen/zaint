package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController

class ZaintHandTool(
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline,
    override var drawTime: Long
) : ZaintToolBase(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    idlingResource,
    commandManager
) {

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.HAND

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun draw(canvas: Canvas) = Unit

    override fun resetInternalState() = Unit

    override fun handleDown(coordinate: PointF?): Boolean = true

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean = true

    override fun handleUp(coordinate: PointF?): Boolean = true

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun handToolMode(): Boolean = true
}
