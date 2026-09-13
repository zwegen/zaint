/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.PerspectiveCommand
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class PerspectiveTool(
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
    override val toolType: ZaintToolKind = ZaintToolKind.PERSPECTIVE
    override var drawTime: Long = 0L
    private val points = Array(POINT_COUNT) { PointF() }
    private var activePoint = NO_ACTIVE_POINT
    private var previewLayer: ZaintLayerContracts.ZaintLayer? = null
    private var previewOriginalBitmap: android.graphics.Bitmap? = null
    private val previewScope = CoroutineScope(Dispatchers.Default)
    private var previewJob: Job? = null
    private var previewGeneration = 0

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = LINE_STROKE_WIDTH
    }
    private val lineShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = LINE_SHADOW_STROKE_WIDTH
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val polygonPath = Path()

    init {
        resetPoints()
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun draw(canvas: Canvas) {
        linePaint.strokeWidth = scaled(LINE_STROKE_WIDTH)
        lineShadowPaint.strokeWidth = scaled(LINE_SHADOW_STROKE_WIDTH)
        polygonPath.reset()
        polygonPath.moveTo(points[TOP_LEFT].x, points[TOP_LEFT].y)
        for (index in TOP_RIGHT until POINT_COUNT) {
            polygonPath.lineTo(points[index].x, points[index].y)
        }
        polygonPath.close()
        canvas.drawPath(polygonPath, lineShadowPaint)
        canvas.drawPath(polygonPath, linePaint)

        val handleRadius = scaled(HANDLE_RADIUS)
        val handleShadowRadius = scaled(HANDLE_SHADOW_RADIUS)
        points.forEach {
            canvas.drawCircle(it.x, it.y, handleShadowRadius, handleShadowPaint)
            canvas.drawCircle(it.x, it.y, handleRadius, handlePaint)
        }
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        activePoint = nearestPointIndex(coordinate)
        if (activePoint != NO_ACTIVE_POINT) {
            startPreview()
        }
        return activePoint != NO_ACTIVE_POINT
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        if (activePoint == NO_ACTIVE_POINT) {
            return true
        }
        points[activePoint].set(coordinate.x, coordinate.y)
        updatePreview()
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        activePoint = NO_ACTIVE_POINT
        return super.handleUp(coordinate)
    }

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun resetInternalState(stateChange: Tool.StateChange) {
        restorePreview()
        if (stateChange == Tool.StateChange.NEW_IMAGE_LOADED) {
            resetPoints()
        }
    }

    fun applyPerspective() {
        if (!isValidQuad()) {
            contextCallback.showNotification(R.string.perspective_invalid_selection)
            return
        }
        restorePreview()
        commandManager.addCommand(
            commandFactory.createPerspectiveCommand(
                sourcePoints(),
                maximumBitmapResolution()
            )
        )
        resetPoints()
        workspace.invalidate()
    }

    /** Discards the temporary preview when another editor action takes over. */
    fun finishPendingPerspectiveOnNavigation() {
        restorePreview()
    }

    private fun startPreview() {
        if (previewOriginalBitmap != null) {
            return
        }
        val layer = workspace.layerModel.currentLayer ?: return
        previewLayer = layer
        previewOriginalBitmap = layer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    }

    private fun updatePreview() {
        val original = previewOriginalBitmap ?: return
        val layer = previewLayer ?: return
        val previewPoints = sourcePoints()
        val generation = ++previewGeneration
        previewJob?.cancel()
        previewJob = previewScope.launch {
            var result: android.graphics.Bitmap? = null
            try {
                result = createScaledPreview(original, previewPoints)
                withContext(Dispatchers.Main) {
                    val previewStillCurrent = generation == previewGeneration &&
                        previewOriginalBitmap != null && previewLayer === layer
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

    /** Warps a capped source, then expands it back to canvas size for a responsive temporary preview. */
    private fun createScaledPreview(
        original: android.graphics.Bitmap,
        originalPoints: FloatArray
    ): android.graphics.Bitmap? {
        val longestSide = max(original.width, original.height)
        val scale = minOf(1f, MAX_PREVIEW_SIDE.toFloat() / longestSide.toFloat())
        val previewSource = if (scale == 1f) {
            original
        } else {
            android.graphics.Bitmap.createScaledBitmap(
                original,
                (original.width * scale).roundToInt().coerceAtLeast(1),
                (original.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        }
        return try {
            val scaledPoints = FloatArray(originalPoints.size) { index -> originalPoints[index] * scale }
            val warpedPreview = PerspectiveCommand.transformBitmap(previewSource, scaledPoints) ?: return null
            if (scale == 1f) {
                warpedPreview
            } else {
                android.graphics.Bitmap.createScaledBitmap(
                    warpedPreview,
                    original.width,
                    original.height,
                    true
                ).also { warpedPreview.recycle() }
            }
        } finally {
            if (previewSource !== original) {
                previewSource.recycle()
            }
        }
    }

    private fun restorePreview() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        val original = previewOriginalBitmap
        val layer = previewLayer
        if (original != null && layer != null) {
            layer.bitmap = original.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        }
        original?.recycle()
        previewOriginalBitmap = null
        previewLayer = null
        workspace.invalidate()
    }

    private fun resetPoints() {
        val right = (workspace.width - 1).coerceAtLeast(0).toFloat()
        val bottom = (workspace.height - 1).coerceAtLeast(0).toFloat()
        points[TOP_LEFT].set(0f, 0f)
        points[TOP_RIGHT].set(right, 0f)
        points[BOTTOM_RIGHT].set(right, bottom)
        points[BOTTOM_LEFT].set(0f, bottom)
        activePoint = NO_ACTIVE_POINT
    }

    private fun nearestPointIndex(coordinate: PointF): Int {
        var nearestIndex = NO_ACTIVE_POINT
        var nearestDistance = Float.MAX_VALUE
        points.forEachIndexed { index, point ->
            val distance = distance(point, coordinate)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        return if (nearestDistance <= touchTolerance()) nearestIndex else NO_ACTIVE_POINT
    }

    private fun isValidQuad(): Boolean =
        abs(polygonArea()) >= MIN_POLYGON_AREA &&
            isTurnDirectionConsistent()

    private fun isTurnDirectionConsistent(): Boolean {
        var sign = 0
        for (index in 0 until POINT_COUNT) {
            val a = points[index]
            val b = points[(index + 1) % POINT_COUNT]
            val c = points[(index + 2) % POINT_COUNT]
            val cross = ((b.x - a.x) * (c.y - b.y)) - ((b.y - a.y) * (c.x - b.x))
            if (abs(cross) < MIN_CROSS_VALUE) {
                continue
            }
            val currentSign = if (cross > 0) 1 else -1
            if (sign == 0) {
                sign = currentSign
            } else if (sign != currentSign) {
                return false
            }
        }
        return sign != 0
    }

    private fun polygonArea(): Float {
        var sum = 0f
        for (index in 0 until POINT_COUNT) {
            val current = points[index]
            val next = points[(index + 1) % POINT_COUNT]
            sum += current.x * next.y - next.x * current.y
        }
        return sum / 2f
    }

    private fun sourcePoints(): FloatArray = floatArrayOf(
        points[TOP_LEFT].x, points[TOP_LEFT].y,
        points[TOP_RIGHT].x, points[TOP_RIGHT].y,
        points[BOTTOM_RIGHT].x, points[BOTTOM_RIGHT].y,
        points[BOTTOM_LEFT].x, points[BOTTOM_LEFT].y
    )

    private fun maximumBitmapResolution(): Int =
        (workspace.width.toLong() * workspace.height.toLong() * MAXIMUM_BITMAP_SIZE_FACTOR)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    private fun scaled(value: Float): Float =
        value / workspace.scale.coerceAtLeast(MIN_SCALE)

    private fun touchTolerance(): Float =
        HANDLE_TOUCH_TOLERANCE / workspace.scale.coerceAtLeast(MIN_SCALE)

    private fun distance(a: PointF, b: PointF): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    companion object {
        private const val POINT_COUNT = 4
        private const val TOP_LEFT = 0
        private const val TOP_RIGHT = 1
        private const val BOTTOM_RIGHT = 2
        private const val BOTTOM_LEFT = 3
        private const val NO_ACTIVE_POINT = -1
        private const val LINE_STROKE_WIDTH = 2f
        private const val LINE_SHADOW_STROKE_WIDTH = 4f
        private const val HANDLE_RADIUS = 12f
        private const val HANDLE_SHADOW_RADIUS = 15f
        private const val HANDLE_TOUCH_TOLERANCE = 36f
        private const val MIN_SCALE = 0.1f
        private const val MIN_POLYGON_AREA = 64f
        private const val MIN_CROSS_VALUE = 0.01f
        private const val MAXIMUM_BITMAP_SIZE_FACTOR = 10L
        private const val MAX_PREVIEW_SIDE = 1024
    }
}
