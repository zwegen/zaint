/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class PlaceTool(
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
    override val toolType: ZaintToolKind = ZaintToolKind.PLACE
    override var drawTime: Long = 0L

    private val contentBounds = Rect()
    private val drawBounds = RectF()
    private val transformMatrix = Matrix()
    private val outlinePath = Path()
    private val handlePath = Path()
    private val transformedCorners = FloatArray(CORNER_COORDINATE_COUNT)
    private val rotateHandlePoint = FloatArray(POINT_COORDINATE_COUNT)
    private val topCenterPoint = FloatArray(POINT_COORDINATE_COUNT)
    private var hasContent = false
    private var activeGesture = Gesture.NONE
    private var deltaX = 0f
    private var deltaY = 0f
    private var rotationDegrees = 0f
    private var rotationStartAngle = 0f
    private var rotationStartDegrees = 0f
    private var scaleFactor = 1f
    private var scaleStartDistance = 0f
    private var scaleStartFactor = 1f
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
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    init {
        refreshContentBounds()
    }

    override fun toolPositionCoordinates(coordinate: PointF): PointF = coordinate

    override fun draw(canvas: Canvas) {
        if (!hasContent) {
            refreshContentBounds()
        }
        val bitmap = workspace.layerModel.currentLayer?.bitmap
        if (!hasContent || bitmap == null || bitmap.isRecycled) {
            return
        }
        updateTransform()
        canvas.drawBitmap(bitmap, transformMatrix, bitmapPaint)
        updateOutline()
        linePaint.strokeWidth = scaled(LINE_STROKE_WIDTH)
        lineShadowPaint.strokeWidth = scaled(LINE_SHADOW_STROKE_WIDTH)
        canvas.drawPath(outlinePath, lineShadowPaint)
        canvas.drawPath(outlinePath, linePaint)
        canvas.drawPath(handlePath, lineShadowPaint)
        canvas.drawPath(handlePath, linePaint)
        for (index in 0 until CORNER_COORDINATE_COUNT step POINT_COORDINATE_COUNT) {
            canvas.drawCircle(transformedCorners[index], transformedCorners[index + 1], scaled(HANDLE_RADIUS), lineShadowPaint)
            canvas.drawCircle(transformedCorners[index], transformedCorners[index + 1], scaled(HANDLE_RADIUS), linePaint)
        }
        canvas.drawCircle(rotateHandlePoint[0], rotateHandlePoint[1], scaled(HANDLE_RADIUS), lineShadowPaint)
        canvas.drawCircle(rotateHandlePoint[0], rotateHandlePoint[1], scaled(HANDLE_RADIUS), linePaint)
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        if (!hasContent) {
            refreshContentBounds()
        }
        if (!hasContent) {
            contextCallback.showNotification(R.string.place_empty_layer)
            return false
        }
        activeGesture = when {
            hitRotateHandle(coordinate) -> {
                val center = transformedCenter()
                rotationStartAngle = angleBetween(center, coordinate)
                rotationStartDegrees = rotationDegrees
                Gesture.ROTATE
            }
            hitScaleHandle(coordinate) -> {
                val center = transformedCenter()
                scaleStartDistance = distanceBetween(center, coordinate).coerceAtLeast(MIN_SCALE_DISTANCE)
                scaleStartFactor = scaleFactor
                Gesture.SCALE
            }
            hitTest(coordinate) -> Gesture.MOVE
            else -> Gesture.NONE
        }
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        return activeGesture != Gesture.NONE
    }

    override fun handleDownAnimations(coordinate: PointF?) = Unit

    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        if (activeGesture == Gesture.NONE) {
            return true
        }
        when (activeGesture) {
            Gesture.MOVE -> {
                val previous = previousEventCoordinate ?: coordinate
                deltaX += coordinate.x - previous.x
                deltaY += coordinate.y - previous.y
            }
            Gesture.ROTATE -> {
                val center = transformedCenter()
                rotationDegrees = rotationStartDegrees + angleBetween(center, coordinate) - rotationStartAngle
            }
            Gesture.SCALE -> {
                val center = transformedCenter()
                val distance = distanceBetween(center, coordinate).coerceAtLeast(MIN_SCALE_DISTANCE)
                scaleFactor = (scaleStartFactor * distance / scaleStartDistance).coerceAtLeast(MIN_LAYER_SCALE)
            }
            Gesture.NONE -> Unit
        }
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        workspace.invalidate()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        activeGesture = Gesture.NONE
        return super.handleUp(coordinate)
    }

    override fun handleUpAnimations(coordinate: PointF?) = Unit

    override fun resetInternalState(stateChange: Tool.StateChange) {
        if (stateChange == Tool.StateChange.NEW_IMAGE_LOADED || stateChange == Tool.StateChange.RESET_INTERNAL_STATE) {
            deltaX = 0f
            deltaY = 0f
            rotationDegrees = 0f
            scaleFactor = 1f
            activeGesture = Gesture.NONE
            refreshContentBounds()
        }
    }

    fun applyPlacement() {
        if (!hasContent) {
            refreshContentBounds()
        }
        if (!hasContent) {
            contextCallback.showNotification(R.string.place_empty_layer)
            return
        }
        if (abs(deltaX) < MIN_TRANSFORM_DELTA && abs(deltaY) < MIN_TRANSFORM_DELTA &&
            abs(rotationDegrees) < MIN_ROTATION_DELTA && abs(scaleFactor - 1f) < MIN_SCALE_DELTA) {
            return
        }
        commandManager.addCommand(commandFactory.createPlaceLayerCommand(deltaX, deltaY, rotationDegrees, scaleFactor))
        deltaX = 0f
        deltaY = 0f
        rotationDegrees = 0f
        scaleFactor = 1f
        refreshContentBounds()
        workspace.invalidate()
    }

    private fun hitTest(coordinate: PointF): Boolean {
        updateTransform()
        transformMatrix.mapRect(drawBounds, RectF(contentBounds))
        val tolerance = touchTolerance()
        drawBounds.inset(-tolerance, -tolerance)
        return drawBounds.contains(coordinate.x, coordinate.y)
    }

    private fun hitRotateHandle(coordinate: PointF): Boolean {
        updateTransform()
        updateOutline()
        val tolerance = touchTolerance()
        val dx = coordinate.x - rotateHandlePoint[0]
        val dy = coordinate.y - rotateHandlePoint[1]
        return dx * dx + dy * dy <= tolerance * tolerance
    }

    private fun hitScaleHandle(coordinate: PointF): Boolean {
        updateTransform()
        updateOutline()
        val tolerance = touchTolerance()
        for (index in 0 until CORNER_COORDINATE_COUNT step POINT_COORDINATE_COUNT) {
            val cornerDx = coordinate.x - transformedCorners[index]
            val cornerDy = coordinate.y - transformedCorners[index + 1]
            if (cornerDx * cornerDx + cornerDy * cornerDy <= tolerance * tolerance) {
                return true
            }
        }
        return false
    }

    private fun updateTransform() {
        val pivotX = contentBounds.exactCenterX()
        val pivotY = contentBounds.exactCenterY()
        transformMatrix.reset()
        transformMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
        transformMatrix.postRotate(rotationDegrees, pivotX, pivotY)
        transformMatrix.postTranslate(deltaX, deltaY)
    }

    private fun updateOutline() {
        transformedCorners[0] = contentBounds.left.toFloat()
        transformedCorners[1] = contentBounds.top.toFloat()
        transformedCorners[2] = contentBounds.right.toFloat()
        transformedCorners[3] = contentBounds.top.toFloat()
        transformedCorners[4] = contentBounds.right.toFloat()
        transformedCorners[5] = contentBounds.bottom.toFloat()
        transformedCorners[6] = contentBounds.left.toFloat()
        transformedCorners[7] = contentBounds.bottom.toFloat()
        transformMatrix.mapPoints(transformedCorners)
        outlinePath.reset()
        outlinePath.moveTo(transformedCorners[0], transformedCorners[1])
        outlinePath.lineTo(transformedCorners[2], transformedCorners[3])
        outlinePath.lineTo(transformedCorners[4], transformedCorners[5])
        outlinePath.lineTo(transformedCorners[6], transformedCorners[7])
        outlinePath.close()

        updateHandlePoints()
        handlePath.reset()
        handlePath.moveTo(topCenterPoint[0], topCenterPoint[1])
        handlePath.lineTo(rotateHandlePoint[0], rotateHandlePoint[1])
    }

    private fun updateHandlePoints() {
        topCenterPoint[0] = contentBounds.exactCenterX()
        topCenterPoint[1] = contentBounds.top.toFloat()
        rotateHandlePoint[0] = contentBounds.exactCenterX()
        rotateHandlePoint[1] = contentBounds.top.toFloat() - scaled(ROTATE_HANDLE_OFFSET)
        transformMatrix.mapPoints(topCenterPoint)
        transformMatrix.mapPoints(rotateHandlePoint)
    }

    private fun transformedCenter(): PointF =
        PointF(contentBounds.exactCenterX() + deltaX, contentBounds.exactCenterY() + deltaY)

    private fun angleBetween(center: PointF, point: PointF): Float =
        Math.toDegrees(atan2(point.y - center.y, point.x - center.x).toDouble()).toFloat()

    private fun distanceBetween(center: PointF, point: PointF): Float =
        hypot(point.x - center.x, point.y - center.y)

    private fun refreshContentBounds() {
        val bitmap = workspace.layerModel.currentLayer?.bitmap
        if (bitmap == null || bitmap.isRecycled) {
            hasContent = false
            contentBounds.setEmpty()
            return
        }
        contentBounds.set(0, 0, bitmap.width, bitmap.height)
        hasContent = bitmap.width > 0 && bitmap.height > 0
    }

    private fun scaled(value: Float): Float =
        value / workspace.scale.coerceAtLeast(MIN_SCALE)

    private fun touchTolerance(): Float =
        HANDLE_TOUCH_TOLERANCE / workspace.scale.coerceAtLeast(MIN_SCALE)

    companion object {
        private const val LINE_STROKE_WIDTH = 2.5f
        private const val LINE_SHADOW_STROKE_WIDTH = 5f
        private const val HANDLE_TOUCH_TOLERANCE = 48f
        private const val HANDLE_RADIUS = 11f
        private const val ROTATE_HANDLE_OFFSET = 54f
        private const val MIN_SCALE = 0.1f
        private const val MIN_LAYER_SCALE = 0.05f
        private const val MIN_SCALE_DISTANCE = 1f
        private const val MIN_TRANSFORM_DELTA = 0.5f
        private const val MIN_ROTATION_DELTA = 0.1f
        private const val MIN_SCALE_DELTA = 0.001f
        private const val CORNER_COORDINATE_COUNT = 8
        private const val POINT_COORDINATE_COUNT = 2
    }

    private enum class Gesture {
        NONE, MOVE, ROTATE, SCALE
    }
}
