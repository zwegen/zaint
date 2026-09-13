package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintDocumentAngleRotation
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Draw a reference edge and rotate the complete document to its nearest axis. */
class AlignTool(
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
    override val toolType: ZaintToolKind = ZaintToolKind.ALIGN
    override var drawTime: Long = 0L

    private var start: PointF? = null
    private var end: PointF? = null
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(linePaint).apply { color = Color.BLACK }

    init {
        toolOptionsViewController.showCheckmark()
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun draw(canvas: Canvas) {
        val lineStart = start ?: return
        val lineEnd = end ?: return
        val scale = workspace.scale.coerceAtLeast(MIN_SCALE)
        shadowPaint.strokeWidth = SHADOW_STROKE_WIDTH / scale
        linePaint.strokeWidth = LINE_STROKE_WIDTH / scale
        canvas.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y, shadowPaint)
        canvas.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y, linePaint)
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        start = PointF(coordinate.x, coordinate.y)
        end = PointF(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        if (start == null) return false
        end = PointF(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        coordinate?.let { end = PointF(it.x, it.y) }
        workspace.invalidate()
        return super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun resetInternalState(stateChange: Tool.StateChange) {
        if (stateChange == Tool.StateChange.NEW_IMAGE_LOADED) clearLine()
    }

    fun applyAlignment(): Boolean {
        val lineStart = start ?: return false
        val lineEnd = end ?: return false
        val correction = correctionAngle(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y)
        if (correction == null) return false
        commandManager.addCommand(ZaintDocumentAngleRotation(correction))
        clearLine()
        workspace.invalidate()
        return true
    }

    private fun clearLine() {
        start = null
        end = null
    }

    companion object {
        private const val MIN_SCALE = 0.1f
        private const val MIN_LINE_LENGTH = 12f
        private const val LINE_STROKE_WIDTH = 2f
        private const val SHADOW_STROKE_WIDTH = 4f

        fun correctionAngle(startX: Float, startY: Float, endX: Float, endY: Float): Float? {
            val dx = endX - startX
            val dy = endY - startY
            if (dx * dx + dy * dy < MIN_LINE_LENGTH * MIN_LINE_LENGTH) return null

            val sourceAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            val radians = Math.toRadians(sourceAngle.toDouble())
            val targetAngle = if (abs(cos(radians)) >= abs(sin(radians))) {
                0f
            } else if (sourceAngle >= 0f) {
                90f
            } else {
                -90f
            }
            return targetAngle - sourceAngle
        }
    }
}
