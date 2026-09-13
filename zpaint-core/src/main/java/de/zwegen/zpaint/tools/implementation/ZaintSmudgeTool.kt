package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.common.ZaintBrushOptionsListener
import de.zwegen.zpaint.tools.common.CommonBrushPreviewListener
import de.zwegen.zpaint.tools.options.ZaintSmudgeOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.min
import kotlin.math.sqrt

const val PERCENT_100 = 100f
const val BITMAP_ROTATION_FACTOR = -0.0f
const val DEFAULT_PRESSURE_IN_PERCENT = 50
const val MAX_PRESSURE = 1f
const val MIN_PRESSURE = 0.85f
const val DEFAULT_DRAG_IN_PERCENT = 50
const val DISTANCE_SMOOTHING = 3f
const val DRAW_THRESHOLD = 0.8f
const val PRESSURE_UPDATE_STEP = 0.004f
private const val SIZE_PREVIEW_DURATION_MS = 700L
private const val MIN_CURSOR_SCALE = 0.1f
private const val MIN_SIZE_CONTROL_VALUE = 1
private const val MAX_SIZE_CONTROL_VALUE = 100
private const val MIN_SIZE_PERCENT = 1f
private const val MAX_SIZE_PERCENT = 12f

class ZaintSmudgeTool(
    smudgeOptions: ZaintSmudgeOptions,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline
) : ZaintToolBase(contextCallback, toolOptionsViewController, toolPaint, workspace, idlingResource, commandManager) {

    override var drawTime: Long = 0
    override fun handleUpAnimations(coordinate: PointF?) {
        super.handleUp(coordinate)
    }

    override fun handleDownAnimations(coordinate: PointF?) {
        super.handleDown(coordinate)
    }

    private var currentBitmap: Bitmap? = null
    private var prevPoint: PointF? = null
    private var numOfPointsOnPath = -1
    private var cursorCenter: PointF? = null
    private var isCursorVisible = false
    private val previewHandler = Handler(Looper.getMainLooper())
    private val hideSizePreview = Runnable {
        if (currentBitmap == null) isCursorVisible = false
        workspace.invalidate()
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
    }
    private val cursorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
    }

    @VisibleForTesting
    var maxPressure = 0f

    @VisibleForTesting
    var pressure = maxPressure

    private var sizeControlValue = toolPaint.strokeWidth.toInt()
        .coerceIn(MIN_SIZE_CONTROL_VALUE, MAX_SIZE_CONTROL_VALUE)

    private var dragPercent = DEFAULT_DRAG_IN_PERCENT

    @VisibleForTesting
    var maxSmudgeSize = resolveSmudgeSize(sizeControlValue)

    @VisibleForTesting
    var minSmudgeSize = 0f

    @VisibleForTesting
    val pointArray = mutableListOf<PointF>()

    override val toolType: ZaintToolKind
        get() = ZaintToolKind.SMUDGE

    init {
        toolPaint.strokeCap = Paint.Cap.ROUND
        smudgeOptions.setListener(ZaintBrushOptionsListener(this))
        smudgeOptions.setPreviewState(
            CommonBrushPreviewListener(
                toolPaint,
                toolType
            )
        )
        smudgeOptions.showPaint(toolPaint.paint)
        smudgeOptions.showStrokeCap(toolPaint.strokeCap)
        smudgeOptions.setSmudgeListener(object : ZaintSmudgeOptions.Listener {
            override fun onPressurePercentSelected(percent: Int) {
                updatePressure(percent)
            }

            override fun onDragPercentSelected(percent: Int) {
                updateDrag(percent)
            }

        })

        updatePressure(DEFAULT_PRESSURE_IN_PERCENT)
        updateDrag(DEFAULT_DRAG_IN_PERCENT)
    }

    override fun resetInternalState() {
        previewHandler.removeCallbacks(hideSizePreview)
        currentBitmap?.recycle()
        currentBitmap = null
        prevPoint = null
        pointArray.clear()
        pressure = maxPressure
        cursorCenter = null
        isCursorVisible = false
    }

    fun updatePressure(pressureInPercent: Int) {
        val onePercent = (MAX_PRESSURE - MIN_PRESSURE) / PERCENT_100
        maxPressure = MIN_PRESSURE + onePercent * pressureInPercent
        pressure = maxPressure
    }

    fun updateDrag(dragInPercent: Int) {
        dragPercent = dragInPercent.coerceIn(0, PERCENT_100.toInt())
        minSmudgeSize = maxSmudgeSize / PERCENT_100 * dragPercent
    }

    override fun changePaintStrokeWidth(strokeWidth: Int) {
        sizeControlValue = strokeWidth.coerceIn(MIN_SIZE_CONTROL_VALUE, MAX_SIZE_CONTROL_VALUE)
        super.changePaintStrokeWidth(sizeControlValue)
        updateSmudgeSize()
        val surfaceCenter = PointF(workspace.surfaceWidth / 2f, workspace.surfaceHeight / 2f)
        cursorCenter = workspace.getCanvasPointFromSurfacePoint(surfaceCenter)
        isCursorVisible = true
        previewHandler.removeCallbacks(hideSizePreview)
        previewHandler.postDelayed(hideSizePreview, SIZE_PREVIEW_DURATION_MS)
        workspace.invalidate()
    }

    private fun updateSmudgeSize() {
        maxSmudgeSize = resolveSmudgeSize(sizeControlValue)
        updateDrag(dragPercent)
    }

    private fun resolveSmudgeSize(controlValue: Int): Float {
        val rangeFraction = (controlValue - MIN_SIZE_CONTROL_VALUE).toFloat() /
            (MAX_SIZE_CONTROL_VALUE - MIN_SIZE_CONTROL_VALUE)
        val sizePercent = MIN_SIZE_PERCENT +
            rangeFraction * (MAX_SIZE_PERCENT - MIN_SIZE_PERCENT)
        val shorterImageSide = min(workspace.width, workspace.height).coerceAtLeast(1)
        return (shorterImageSide * sizePercent / PERCENT_100).coerceAtLeast(1f)
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false

        previewHandler.removeCallbacks(hideSizePreview)
        cursorCenter = PointF(coordinate.x, coordinate.y)
        isCursorVisible = true
        workspace.invalidate()

        updateSmudgeSize()

        val layerBitmap = workspace.bitmapOfCurrentLayer
        currentBitmap = Bitmap.createBitmap(
            maxSmudgeSize.toInt(),
            maxSmudgeSize.toInt(),
            Bitmap.Config.ARGB_8888
        )
        currentBitmap?.let {
            Canvas(it).apply {
                translate(-coordinate.x + maxSmudgeSize / 2f, -coordinate.y + maxSmudgeSize / 2f)
                rotate(BITMAP_ROTATION_FACTOR, coordinate.x, coordinate.y)
                layerBitmap?.let { bitmap ->
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }

            if (toolPaint.strokeCap == Paint.Cap.ROUND) {
                currentBitmap = getBitmapClippedCircle(it)
            }
        }

        if (!currentBitmapHasColor()) {
            currentBitmap?.recycle()
            currentBitmap = null
            return false
        }

        prevPoint = PointF(coordinate.x, coordinate.y)
        prevPoint?.apply {
            pointArray.add(PointF(x, y))
        }

        return true
    }

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false

        cursorCenter = PointF(coordinate.x, coordinate.y)
        isCursorVisible = true
        workspace.invalidate()

        if (currentBitmap != null) {
            if (pressure < DRAW_THRESHOLD) { // Needed to stop drawing preview when bitmap becomes too transparent. Has no effect on final drawing.
                return false
            }

            prevPoint?.apply {
                val x1 = coordinate.x - x
                val y1 = coordinate.y - y

                val distance = (sqrt(x1 * x1 + y1 * y1) / DISTANCE_SMOOTHING).toInt()
                val xInterval = x1 / distance
                val yInterval = y1 / distance

                repeat(distance) {
                    x += xInterval
                    y += yInterval

                    pressure -= PRESSURE_UPDATE_STEP

                    pointArray.add(PointF(x, y))
                }
            }
            return true
        } else {
            return false
        }
    }

    private fun getBitmapClippedCircle(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val path = Path()
        path.addCircle(
            (width / 2).toFloat(),
            (height / 2).toFloat(),
            kotlin.math.min(width, height / 2).toFloat(),
            Path.Direction.CCW
        )
        val canvas = Canvas(outputBitmap)
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return outputBitmap
    }

    private fun currentBitmapHasColor(): Boolean {
        currentBitmap?.apply {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (getPixel(x, y) != 0) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        coordinate ?: return false

        isCursorVisible = false
        cursorCenter = null
        workspace.invalidate()

        if (pointArray.isNotEmpty() && currentBitmap != null) {
            currentBitmap?.let {
                val command = commandFactory.createSmudgePathCommand(
                    it,
                    pointArray,
                    maxPressure,
                    maxSmudgeSize,
                    minSmudgeSize,
                    workspace.width,
                    workspace.height
                )
                commandManager.addCommand(command)
            }

            numOfPointsOnPath = if (numOfPointsOnPath < 0) {
                pointArray.size
            } else {
                (numOfPointsOnPath + pointArray.size) / 2
            }

            pressure = maxPressure
            pointArray.clear()
            currentBitmap?.recycle()
            currentBitmap = null
            prevPoint = null
            return true
        } else {
            return false
        }
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun draw(canvas: Canvas) {
        if (pointArray.isNotEmpty()) {
            val pointPath = pointArray.toMutableList()

            val step = if (numOfPointsOnPath < 0) {
                (maxSmudgeSize - minSmudgeSize) / pointPath.size
            } else {
                (maxSmudgeSize - minSmudgeSize) / numOfPointsOnPath
            }

            var size = maxSmudgeSize
            var pressure = maxPressure
            val colorMatrix = ColorMatrix()
            val paint = Paint()
            var bitmap = currentBitmap?.copy(Bitmap.Config.ARGB_8888, false)

            pointPath.forEach {
                colorMatrix.setScale(1f, 1f, 1f, pressure)
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

                val newBitmap = Bitmap.createBitmap(
                    maxSmudgeSize.toInt(),
                    maxSmudgeSize.toInt(),
                    Bitmap.Config.ARGB_8888
                )

                Canvas(newBitmap).apply {
                    bitmap?.let { currentBitmap ->
                        drawBitmap(currentBitmap, 0f, 0f, paint)
                    }
                }

                bitmap?.recycle()
                bitmap = newBitmap

                val rect = RectF(-size / 2f, -size / 2f, size / 2f, size / 2f)
                with(canvas) {
                    save()
                    clipRect(0, 0, workspace.width, workspace.height)
                    translate(it.x, it.y)
                    bitmap?.let { currentBitmap ->
                        drawBitmap(currentBitmap, null, rect, Paint(Paint.DITHER_FLAG))
                    }
                    restore()
                }
                size -= step
                pressure -= PRESSURE_UPDATE_STEP
            }

            bitmap?.recycle()
        }

        if (isCursorVisible) {
            cursorCenter?.let { center ->
                val scale = workspace.scale.coerceAtLeast(MIN_CURSOR_SCALE)
                cursorShadowPaint.strokeWidth = 5f / scale
                cursorPaint.strokeWidth = 2f / scale
                val radius = maxSmudgeSize / 2f
                canvas.drawCircle(center.x, center.y, radius, cursorShadowPaint)
                canvas.drawCircle(center.x, center.y, radius, cursorPaint)
            }
        }
    }

}
