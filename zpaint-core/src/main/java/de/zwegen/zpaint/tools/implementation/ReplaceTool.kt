package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ReplaceToolOptionsView
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.min

class ReplaceTool(
    private val optionsView: ReplaceToolOptionsView,
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
    override val toolType: ZaintToolKind = ZaintToolKind.REPLACE
    override var drawTime: Long = 0L
    private val maskPath = ZaintStrokePath()
    private val previewLock = Any()
    private val strokeStarts = mutableListOf<PointF>()
    private val maskPoints = mutableListOf<PointF>()
    private var previewCenter: PointF? = null
    private var sizePreviewCenter: PointF? = null
    private var sourcePreviewCenter: PointF? = null
    private var sourceHintCenter: PointF? = null
    private var brushSizeControlValue = lastBrushSizeControlValue
        .toInt()
        .coerceIn(MIN_BRUSH_SIZE, MAX_BRUSH_SIZE)
    private var brushSize = resolveBrushSize(brushSizeControlValue)
    private var hasMarking = false
    private var phase = Phase.TARGET
    private val previewHandler = Handler(Looper.getMainLooper())
    private val hideSizePreview = Runnable {
        synchronized(previewLock) {
            if (!hasMarking) sizePreviewCenter = null
        }
        workspace.invalidate()
    }
    private val hideSourceHint = Runnable {
        synchronized(previewLock) { sourceHintCenter = null }
        workspace.invalidate()
    }
    private val markingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 82, 82)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val markingPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 82, 82)
        style = Paint.Style.FILL
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 82, 82)
        style = Paint.Style.STROKE
        strokeWidth = CURSOR_STROKE_WIDTH
    }
    private val cursorOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = CURSOR_OUTLINE_STROKE_WIDTH
    }
    private val sourceCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 76, 175, 80)
        style = Paint.Style.STROKE
        strokeWidth = CURSOR_STROKE_WIDTH
    }
    private val sourceHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val sourceHintOutlinePaint = Paint(sourceHintPaint).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    init {
        optionsView.setBrushSizeRange(MIN_BRUSH_SIZE, MAX_BRUSH_SIZE, brushSize.toInt())
        optionsView.setCallback(object : ReplaceToolOptionsView.Callback {
            override fun setBrushSize(size: Int) {
                synchronized(previewLock) {
                    brushSizeControlValue = size.coerceIn(MIN_BRUSH_SIZE, MAX_BRUSH_SIZE)
                    brushSize = resolveBrushSize(brushSizeControlValue)
                    lastBrushSizeControlValue = brushSizeControlValue.toFloat()
                }
                showSizePreview()
                workspace.invalidate()
            }
        })
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun draw(canvas: Canvas) {
        val preview = synchronized(previewLock) {
            PreviewSnapshot(
                ZaintStrokePath(maskPath),
                strokeStarts.map { PointF(it.x, it.y) },
                previewCenter?.let { PointF(it.x, it.y) },
                sizePreviewCenter?.let { PointF(it.x, it.y) },
                sourcePreviewCenter?.let { PointF(it.x, it.y) },
                sourceHintCenter?.let { PointF(it.x, it.y) },
                brushSize
            )
        }
        val markingPaintSnapshot = Paint(markingPaint).apply { strokeWidth = preview.brushSize }
        val markingPointPaintSnapshot = Paint(markingPointPaint)
        val cursorPaintSnapshot = Paint(cursorPaint)
        val sourceCursorPaintSnapshot = Paint(sourceCursorPaint)
        val cursorOutlinePaintSnapshot = Paint(cursorOutlinePaint)
        val sourceHintPaintSnapshot = Paint(sourceHintPaint)
        val sourceHintOutlinePaintSnapshot = Paint(sourceHintOutlinePaint)
        val scale = workspace.scale.coerceAtLeast(MIN_CURSOR_SCALE)
        cursorOutlinePaintSnapshot.strokeWidth = CURSOR_OUTLINE_STROKE_WIDTH / scale
        cursorPaintSnapshot.strokeWidth = CURSOR_STROKE_WIDTH / scale
        sourceCursorPaintSnapshot.strokeWidth = CURSOR_STROKE_WIDTH / scale
        canvas.save()
        canvas.clipRect(0, 0, workspace.width, workspace.height)
        canvas.drawPath(preview.path, markingPaintSnapshot)
        preview.strokeStarts.forEach { point ->
            canvas.drawCircle(point.x, point.y, preview.brushSize / 2f, markingPointPaintSnapshot)
        }
        (preview.previewCenter ?: preview.sizePreviewCenter)?.let { point ->
            canvas.drawCircle(point.x, point.y, preview.brushSize / 2f, cursorOutlinePaintSnapshot)
            canvas.drawCircle(point.x, point.y, preview.brushSize / 2f, cursorPaintSnapshot)
        }
        preview.sourcePreviewCenter?.let { point ->
            canvas.drawCircle(point.x, point.y, preview.brushSize / 2f, cursorOutlinePaintSnapshot)
            canvas.drawCircle(point.x, point.y, preview.brushSize / 2f, sourceCursorPaintSnapshot)
        }
        preview.sourceHintCenter?.let { point ->
            drawSourceHint(canvas, point, sourceHintPaintSnapshot, sourceHintOutlinePaintSnapshot)
        }
        canvas.restore()
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        val selectsSource = synchronized(previewLock) { phase == Phase.SOURCE }
        if (selectsSource) {
            synchronized(previewLock) {
                sourceHintCenter = null
                sourcePreviewCenter = PointF(coordinate.x, coordinate.y)
                previewCenter = null
            }
            previewHandler.removeCallbacks(hideSourceHint)
            workspace.invalidate()
            return true
        }
        synchronized(previewLock) {
            maskPath.moveTo(coordinate.x, coordinate.y)
            strokeStarts += PointF(coordinate.x, coordinate.y)
            maskPoints += PointF(coordinate.x, coordinate.y)
            previewCenter = PointF(coordinate.x, coordinate.y)
            previousEventCoordinate = PointF(coordinate.x, coordinate.y)
            hasMarking = true
        }
        previewHandler.removeCallbacks(hideSizePreview)
        workspace.invalidate()
        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        synchronized(previewLock) {
            if (phase == Phase.SOURCE) {
                sourcePreviewCenter = PointF(coordinate.x, coordinate.y)
            } else {
                val previous = previousEventCoordinate ?: coordinate
                maskPath.quadTo(
                    previous.x,
                    previous.y,
                    (previous.x + coordinate.x) / 2f,
                    (previous.y + coordinate.y) / 2f
                )
                previousEventCoordinate = PointF(coordinate.x, coordinate.y)
                previewCenter = PointF(coordinate.x, coordinate.y)
            }
        }
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (synchronized(previewLock) { phase == Phase.SOURCE }) {
            val sourceCenter = synchronized(previewLock) {
                coordinate?.let { sourcePreviewCenter = PointF(it.x, it.y) }
                sourcePreviewCenter?.let { PointF(it.x, it.y) }
            }
            return applyReplace(sourceCenter)
        }
        synchronized(previewLock) {
            val end = coordinate ?: previousEventCoordinate
            end?.let { point ->
                previousEventCoordinate?.let {
                    maskPath.lineTo(point.x, point.y)
                    maskPoints += PointF(point.x, point.y)
                }
            }
            previewCenter = end?.let { PointF(it.x, it.y) }
            previousEventCoordinate = null
            phase = Phase.SOURCE
            previewCenter = null
        }
        showSourceHint()
        workspace.invalidate()
        return true
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun resetInternalState(stateChange: Tool.StateChange) {
        previewHandler.removeCallbacks(hideSizePreview)
        previewHandler.removeCallbacks(hideSourceHint)
        // Pinching sends MOVE_CANCELED to the active tool. Once the target is painted, that
        // gesture must only move or zoom the canvas; it must not discard the pending target.
        if (synchronized(previewLock) {
                if (stateChange == Tool.StateChange.MOVE_CANCELED && phase == Phase.SOURCE) {
                    sourcePreviewCenter = null
                    true
                } else {
                    false
                }
            }
        ) {
            workspace.invalidate()
            return
        }
        if (stateChange == Tool.StateChange.NEW_IMAGE_LOADED) {
            synchronized(previewLock) {
                lastBrushSizeControlValue = DEFAULT_BRUSH_SIZE
                brushSizeControlValue = DEFAULT_BRUSH_SIZE.toInt()
                brushSize = resolveBrushSize(brushSizeControlValue)
            }
            optionsView.setBrushSizeRange(MIN_BRUSH_SIZE, MAX_BRUSH_SIZE, DEFAULT_BRUSH_SIZE.toInt())
        }
        clearMarking()
    }

    fun applyReplace(sourceCenter: PointF?): Boolean {
        val selection = synchronized(previewLock) {
            if (!hasMarking || sourceCenter == null) null else ReplaceSelection(
                ZaintStrokePath(maskPath),
                maskPoints.map { PointF(it.x, it.y) },
                brushSize,
                PointF(sourceCenter.x, sourceCenter.y)
            )
        } ?: return false
        commandManager.addCommand(
            commandFactory.createReplaceCommand(
                selection.path,
                selection.points,
                selection.brushSize,
                selection.sourceCenter
            )
        )
        clearMarking()
        workspace.invalidate()
        return true
    }

    private fun resolveBrushSize(controlValue: Int): Float {
        val rangeFraction = (controlValue - MIN_BRUSH_SIZE).toFloat() /
            (MAX_BRUSH_SIZE - MIN_BRUSH_SIZE)
        val sizePercent = MIN_BRUSH_SIZE_PERCENT +
            rangeFraction * (MAX_BRUSH_SIZE_PERCENT - MIN_BRUSH_SIZE_PERCENT)
        val shorterImageSide = min(workspace.width, workspace.height).coerceAtLeast(1)
        return (shorterImageSide * sizePercent / 100f).coerceAtLeast(1f)
    }

    private fun clearMarking() = synchronized(previewLock) {
        maskPath.rewind()
        strokeStarts.clear()
        maskPoints.clear()
        previewCenter = null
        sourcePreviewCenter = null
        sourceHintCenter = null
        previousEventCoordinate = null
        hasMarking = false
        phase = Phase.TARGET
    }

    private fun showSizePreview() {
        val center = workspace.getCanvasPointFromSurfacePoint(
            PointF(workspace.surfaceWidth / 2f, workspace.surfaceHeight / 2f)
        )
        val shouldShow = synchronized(previewLock) {
            if (hasMarking) false else {
                sizePreviewCenter = center
                true
            }
        }
        if (!shouldShow) return
        previewHandler.removeCallbacks(hideSizePreview)
        previewHandler.postDelayed(hideSizePreview, SIZE_PREVIEW_DURATION_MS)
    }

    private fun showSourceHint() {
        val center = workspace.getCanvasPointFromSurfacePoint(
            PointF(workspace.surfaceWidth / 2f, workspace.surfaceHeight / 2f)
        )
        synchronized(previewLock) { sourceHintCenter = center }
        previewHandler.removeCallbacks(hideSourceHint)
        previewHandler.postDelayed(hideSourceHint, SOURCE_HINT_DURATION_MS)
    }

    private fun drawSourceHint(
        canvas: Canvas,
        center: PointF,
        sourceHintPaint: Paint,
        sourceHintOutlinePaint: Paint
    ) {
        val scale = workspace.scale.coerceAtLeast(MIN_SCALE)
        sourceHintPaint.textSize = SOURCE_HINT_TEXT_SIZE_SP *
            contextCallback.displayMetrics.scaledDensity / scale
        sourceHintOutlinePaint.textSize = sourceHintPaint.textSize
        sourceHintOutlinePaint.strokeWidth = SOURCE_HINT_OUTLINE_WIDTH_DP *
            contextCallback.displayMetrics.density / scale
        val fontMetrics = sourceHintPaint.fontMetrics
        val lineHeight = fontMetrics.descent - fontMetrics.ascent
        val firstBaseline = center.y - lineHeight / 2f - fontMetrics.ascent
        contextCallback.context.getString(R.string.replace_tool_source_hint)
            .lineSequence()
            .forEachIndexed { index, line ->
                val baseline = firstBaseline + index * lineHeight
                canvas.drawText(line, center.x, baseline, sourceHintOutlinePaint)
                canvas.drawText(line, center.x, baseline, sourceHintPaint)
            }
    }

    private data class PreviewSnapshot(
        val path: ZaintStrokePath,
        val strokeStarts: List<PointF>,
        val previewCenter: PointF?,
        val sizePreviewCenter: PointF?,
        val sourcePreviewCenter: PointF?,
        val sourceHintCenter: PointF?,
        val brushSize: Float
    )

    private data class ReplaceSelection(
        val path: ZaintStrokePath,
        val points: List<PointF>,
        val brushSize: Float,
        val sourceCenter: PointF
    )

    private companion object {
        const val MIN_BRUSH_SIZE = 1
        const val MAX_BRUSH_SIZE = 100
        const val DEFAULT_BRUSH_SIZE = 8f
        const val MIN_BRUSH_SIZE_PERCENT = 1f
        const val MAX_BRUSH_SIZE_PERCENT = 12f
        const val CURSOR_STROKE_WIDTH = 2f
        const val CURSOR_OUTLINE_STROKE_WIDTH = 5f
        const val MIN_CURSOR_SCALE = 0.1f
        const val SIZE_PREVIEW_DURATION_MS = 700L
        const val SOURCE_HINT_DURATION_MS = 1_000L
        const val SOURCE_HINT_TEXT_SIZE_SP = 20f
        const val SOURCE_HINT_OUTLINE_WIDTH_DP = 2f
        const val MIN_SCALE = 0.1f
        var lastBrushSizeControlValue = DEFAULT_BRUSH_SIZE
    }

    private enum class Phase { TARGET, SOURCE }
}
