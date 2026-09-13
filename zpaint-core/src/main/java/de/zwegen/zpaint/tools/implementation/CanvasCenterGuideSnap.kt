package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Keeps a floating tool aligned with the canvas centre while a drag is close to it.
 *
 * The release range prevents a tool from immediately snapping back after the user has
 * deliberately pulled it away from the centre.
 */
class CanvasCenterGuideSnap(
    private val snapPercent: Float = 0.02f,
    private val guideShowMultiplier: Float = 2f,
    private val minimumDistance: Float = 6f,
    private val maximumDistance: Float = 80f
) {
    private var lockedX = false
    private var lockedY = false
    private var suppressedX = false
    private var suppressedY = false
    private var lockTouchX = 0f
    private var lockTouchY = 0f

    fun resolve(
        positionX: Float,
        positionY: Float,
        touchX: Float?,
        touchY: Float?,
        canvasWidth: Float,
        canvasHeight: Float
    ): Result {
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        val snapDistanceX = snapDistance(canvasWidth)
        val snapDistanceY = snapDistance(canvasHeight)
        val distanceX = abs(positionX - centerX)
        val distanceY = abs(positionY - centerY)

        if (lockedX && touchX != null && abs(touchX - lockTouchX) >= snapDistanceX) {
            lockedX = false
            suppressedX = true
        }
        if (lockedY && touchY != null && abs(touchY - lockTouchY) >= snapDistanceY) {
            lockedY = false
            suppressedY = true
        }
        if (suppressedX && distanceX > snapDistanceX) suppressedX = false
        if (suppressedY && distanceY > snapDistanceY) suppressedY = false

        val snapX = distanceX <= snapDistanceX && !suppressedX
        val snapY = distanceY <= snapDistanceY && !suppressedY
        if (snapX && !lockedX) {
            lockedX = true
            lockTouchX = touchX ?: centerX
        }
        if (snapY && !lockedY) {
            lockedY = true
            lockTouchY = touchY ?: centerY
        }

        return Result(
            x = if (snapX) centerX else positionX,
            y = if (snapY) centerY else positionY,
            showVerticalGuide = distanceX <= snapDistanceX * guideShowMultiplier || lockedX,
            showHorizontalGuide = distanceY <= snapDistanceY * guideShowMultiplier || lockedY
        )
    }

    fun reset() {
        lockedX = false
        lockedY = false
        suppressedX = false
        suppressedY = false
    }

    private fun snapDistance(canvasSize: Float): Float =
        (canvasSize * snapPercent).coerceIn(minimumDistance, maximumDistance)

    data class Result(
        val x: Float,
        val y: Float,
        val showVerticalGuide: Boolean,
        val showHorizontalGuide: Boolean
    )
}

/** Draws the centre guides in a floating-box canvas that has already been translated and rotated. */
object CanvasCenterGuideRenderer {
    private const val GUIDE_ALPHA = 255
    private const val GUIDE_SHADOW_ALPHA = 160
    private const val GUIDE_STROKE_WIDTH = 3.5f
    private const val GUIDE_SHADOW_EXTRA_WIDTH = 2.5f

    fun draw(
        canvas: Canvas,
        canvasWidth: Float,
        canvasHeight: Float,
        toolPositionX: Float,
        toolPositionY: Float,
        boxRotation: Float,
        canvasScale: Float,
        linePaint: Paint,
        showVerticalGuide: Boolean,
        showHorizontalGuide: Boolean
    ) {
        if (!showVerticalGuide && !showHorizontalGuide) return

        if (showVerticalGuide) {
            val start = workspacePointToBoxCanvasPoint(
                canvasWidth / 2f, 0f, toolPositionX, toolPositionY, boxRotation
            )
            val stop = workspacePointToBoxCanvasPoint(
                canvasWidth / 2f, canvasHeight, toolPositionX, toolPositionY, boxRotation
            )
            drawGuideLine(canvas, start.x, start.y, stop.x, stop.y, canvasScale, linePaint)
        }
        if (showHorizontalGuide) {
            val start = workspacePointToBoxCanvasPoint(
                0f, canvasHeight / 2f, toolPositionX, toolPositionY, boxRotation
            )
            val stop = workspacePointToBoxCanvasPoint(
                canvasWidth, canvasHeight / 2f, toolPositionX, toolPositionY, boxRotation
            )
            drawGuideLine(canvas, start.x, start.y, stop.x, stop.y, canvasScale, linePaint)
        }
    }

    private fun workspacePointToBoxCanvasPoint(
        x: Float,
        y: Float,
        toolPositionX: Float,
        toolPositionY: Float,
        boxRotation: Float
    ): FloatPair {
        val radians = Math.toRadians((-boxRotation).toDouble())
        val deltaX = x - toolPositionX
        val deltaY = y - toolPositionY
        return FloatPair(
            (deltaX * cos(radians) - deltaY * sin(radians)).toFloat(),
            (deltaX * sin(radians) + deltaY * cos(radians)).toFloat()
        )
    }

    fun drawGuideLine(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        stopX: Float,
        stopY: Float,
        canvasScale: Float,
        linePaint: Paint
    ) {
        val scale = canvasScale.coerceAtLeast(0.01f)
        linePaint.apply {
            color = Color.argb(GUIDE_SHADOW_ALPHA, 0, 0, 0)
            strokeWidth = (GUIDE_STROKE_WIDTH + GUIDE_SHADOW_EXTRA_WIDTH) / scale
            style = Paint.Style.STROKE
        }
        canvas.drawLine(startX, startY, stopX, stopY, linePaint)
        linePaint.apply {
            color = Color.argb(GUIDE_ALPHA, 80, 190, 255)
            strokeWidth = GUIDE_STROKE_WIDTH / scale
            style = Paint.Style.STROKE
        }
        canvas.drawLine(startX, startY, stopX, stopY, linePaint)
    }

    private data class FloatPair(val x: Float, val y: Float)
}
