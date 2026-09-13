package de.zwegen.zpaint.ui

import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Rect
import androidx.annotation.VisibleForTesting
import kotlin.math.max
import kotlin.math.min

const val MIN_SCALE = 0.1f
const val MAX_SCALE = 100f

private const val PAN_MARGIN = 50f

/**
 * Maps between the bitmap coordinate system and the visible drawing surface.
 *
 * The transformation is deliberately kept here, independently from tools and rendering. Tools
 * can therefore work exclusively with bitmap coordinates while this class owns pan and zoom.
 */
open class Perspective(private var bitmapWidth: Int, private var bitmapHeight: Int) {
    @JvmField var surfaceWidth = 0
    @JvmField var surfaceHeight = 0
    @VisibleForTesting @JvmField var surfaceCenterX = 0f
    @VisibleForTesting @JvmField var surfaceCenterY = 0f
    @VisibleForTesting var surfaceScale = 1f
    @JvmField var surfaceTranslationX = 0f
    @JvmField var surfaceTranslationY = 0f
    @VisibleForTesting var initialTranslationY = 0f
    var callResetScaleAndTransformationOnStartUp = 0
    var oldHeight = 0f

    private var initialTranslationX = 0f

    @set:Synchronized
    var scale: Float
        get() = surfaceScale
        set(value) {
            surfaceScale = value.coerceIn(MIN_SCALE, MAX_SCALE)
        }

    val scaleForCenterBitmap: Float
        get() = if (bitmapWidth <= 0 || surfaceWidth <= 0) 1f
        else min(1f, surfaceWidth.toFloat() / bitmapWidth)

    @Synchronized
    fun setSurfaceFrame(frame: Rect) {
        if (surfaceHeight == 0) oldHeight = frame.bottom.toFloat()
        surfaceWidth = frame.right
        surfaceHeight = frame.bottom
        surfaceCenterX = frame.exactCenterX()
        // Keep the original centre while the keyboard temporarily shrinks the surface.
        surfaceCenterY = max(surfaceCenterY, frame.exactCenterY())
    }

    @Synchronized
    fun setBitmapDimensions(width: Int, height: Int) {
        bitmapWidth = width
        bitmapHeight = height
    }

    @Synchronized
    fun resetScaleAndTranslation() {
        centerBitmap()
        scale = scaleForCenterBitmap
    }

    @Synchronized
    fun resetScaleAndTranslationToFitWidth() {
        centerBitmap()
        scale = if (bitmapWidth > 0 && surfaceWidth > 0) {
            surfaceWidth.toFloat() / bitmapWidth
        } else {
            1f
        }
    }

    @Synchronized
    fun resetScaleAndTranslationWithPadding(totalPadding: Int) {
        centerBitmap()
        val paddedWidth = bitmapWidth + totalPadding.coerceAtLeast(0)
        val paddedHeight = bitmapHeight + totalPadding.coerceAtLeast(0)
        scale = fitScale(paddedWidth) * calculateZoomFactor(paddedWidth, paddedHeight)
    }

    @Synchronized
    fun centerBitmap() {
        if (surfaceWidth == 0 || surfaceHeight == 0) {
            surfaceTranslationX = 0f
            surfaceTranslationY = 0f
        } else {
            surfaceTranslationX = surfaceWidth / 2f - bitmapWidth / 2f
            surfaceTranslationY = surfaceHeight / 2f - bitmapHeight / 2f
        }
        initialTranslationX = surfaceTranslationX
        initialTranslationY = surfaceTranslationY
    }

    @Synchronized
    fun calculateZoomFactor(width: Int = bitmapWidth, height: Int = bitmapHeight): Float {
        if (width <= 0 || height <= 0 || surfaceWidth <= 0 || surfaceHeight <= 0) return 1f
        return if (height > width && height < surfaceHeight) {
            surfaceHeight.toFloat() / height
        } else if (width < surfaceWidth) {
            surfaceWidth.toFloat() / width
        } else {
            1f
        }
    }

    @Synchronized
    fun multiplyScale(factor: Float) {
        scale *= factor
    }

    @Synchronized
    fun translate(dx: Float, dy: Float) {
        if (surfaceScale <= 0f) return
        surfaceTranslationX += dx / surfaceScale
        surfaceTranslationY += dy / surfaceScale
        constrainPan()
    }

    @Synchronized
    fun convertToCanvasFromSurface(point: PointF) {
        point.x = (point.x - surfaceCenterX) / surfaceScale + surfaceCenterX - surfaceTranslationX
        point.y = (point.y - surfaceCenterY) / surfaceScale + surfaceCenterY - surfaceTranslationY
    }

    @Synchronized
    fun convertToSurfaceFromCanvas(point: PointF) {
        point.x = (point.x + surfaceTranslationX - surfaceCenterX) * surfaceScale + surfaceCenterX
        point.y = (point.y + surfaceTranslationY - surfaceCenterY) * surfaceScale + surfaceCenterY
    }

    @Synchronized
    fun getCanvasPointFromSurfacePoint(point: PointF): PointF = PointF(point.x, point.y).also(::convertToCanvasFromSurface)

    @Synchronized
    fun getSurfacePointFromCanvasPoint(point: PointF): PointF = PointF(point.x, point.y).also(::convertToSurfaceFromCanvas)

    @Synchronized
    fun applyToCanvas(canvas: Canvas) {
        canvas.scale(surfaceScale, surfaceScale, surfaceCenterX, surfaceCenterY)
        canvas.translate(surfaceTranslationX, surfaceTranslationY)
    }

    private fun fitScale(width: Int): Float =
        if (width <= 0 || surfaceWidth <= 0) 1f else min(1f, surfaceWidth.toFloat() / width)

    private fun constrainPan() {
        val maxX = bitmapWidth / 2f + (surfaceWidth / 2f - PAN_MARGIN) / surfaceScale
        val maxY = bitmapHeight / 2f + (surfaceHeight / 2f - PAN_MARGIN) / surfaceScale
        surfaceTranslationX = surfaceTranslationX.coerceIn(initialTranslationX - maxX, initialTranslationX + maxX)
        surfaceTranslationY = surfaceTranslationY.coerceIn(initialTranslationY - maxY, initialTranslationY + maxY)
    }
}
