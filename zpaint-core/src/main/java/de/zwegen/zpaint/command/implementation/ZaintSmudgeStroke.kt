package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.implementation.PRESSURE_UPDATE_STEP
import de.zwegen.zpaint.tools.implementation.SmudgeStrokeBounds

/** Replays one self-contained Zaint smudge stroke, optionally as a bounded pixel snapshot. */
class ZaintSmudgeStroke private constructor(
    bitmap: Bitmap,
    pointPath: MutableList<PointF>,
    maxPressure: Float,
    maxSize: Float,
    minSize: Float,
    val affectedBounds: SmudgeStrokeBounds,
    private val isAreaSnapshot: Boolean
) : Command {
    private val request = SmudgeRequest(
        bitmap,
        pointPath.mapTo(mutableListOf()) { PointF(it.x, it.y) },
        maxPressure,
        maxSize,
        minSize
    )

    val originalBitmap: Bitmap
        get() = request.bitmap

    val pointPath: List<PointF>
        get() = request.points.map { PointF(it.x, it.y) }

    val maxPressure: Float
        get() = request.maxPressure

    val maxSize: Float
        get() = request.maxSize

    val minSize: Float
        get() = request.minSize

    constructor(bitmap: Bitmap, pointPath: MutableList<PointF>, maxPressure: Float, maxSize: Float, minSize: Float) :
        this(bitmap, pointPath, maxPressure, maxSize, minSize, SmudgeStrokeBounds(0, 0, bitmap.width, bitmap.height), false)

    constructor(
        sourceBitmap: Bitmap,
        pointPath: MutableList<PointF>,
        maxPressure: Float,
        maxSize: Float,
        minSize: Float,
        affectedBounds: SmudgeStrokeBounds
    ) : this(
        renderAreaSnapshot(sourceBitmap, pointPath, maxPressure, maxSize, minSize, affectedBounds),
        pointPath,
        maxPressure,
        maxSize,
        minSize,
        affectedBounds,
        true
    ) {
        sourceBitmap.recycle()
    }

    override fun run(canvas: Canvas, layerModel: ZaintLayerContracts.Model) {
        if (isAreaSnapshot) {
            canvas.drawBitmap(request.bitmap, affectedBounds.left.toFloat(), affectedBounds.top.toFloat(), null)
        } else {
            renderLegacySmudge(canvas, request.bitmap, request.points, request.maxPressure, request.maxSize, request.minSize)
        }
    }

    override fun freeResources() {
        if (!request.bitmap.isRecycled) request.bitmap.recycle()
    }

    private data class SmudgeRequest(
        val bitmap: Bitmap,
        val points: List<PointF>,
        val maxPressure: Float,
        val maxSize: Float,
        val minSize: Float
    )

    companion object {
        fun fromAreaSnapshot(
            bitmap: Bitmap,
            pointPath: MutableList<PointF>,
            maxPressure: Float,
            maxSize: Float,
            minSize: Float,
            affectedBounds: SmudgeStrokeBounds
        ): ZaintSmudgeStroke = ZaintSmudgeStroke(
            bitmap, pointPath, maxPressure, maxSize, minSize, affectedBounds, true
        )

        private fun renderAreaSnapshot(
            sourceBitmap: Bitmap,
            pointPath: List<PointF>,
            maxPressure: Float,
            maxSize: Float,
            minSize: Float,
            affectedBounds: SmudgeStrokeBounds
        ): Bitmap = Bitmap.createBitmap(affectedBounds.width, affectedBounds.height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                translate(-affectedBounds.left.toFloat(), -affectedBounds.top.toFloat())
                renderLegacySmudge(this, sourceBitmap, pointPath, maxPressure, maxSize, minSize)
            }
        }

        private fun renderLegacySmudge(
            canvas: Canvas,
            sourceBitmap: Bitmap,
            pointPath: List<PointF>,
            maxPressure: Float,
            maxSize: Float,
            minSize: Float
        ) {
            if (pointPath.isEmpty()) return
            val step = (maxSize - minSize) / pointPath.size
            var size = maxSize
            var pressure = maxPressure
            val colorMatrix = ColorMatrix()
            val paint = Paint()
            var bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
            try {
                pointPath.forEach {
                    colorMatrix.setScale(1f, 1f, 1f, pressure)
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                    val nextBitmap = Bitmap.createBitmap(maxSize.toInt(), maxSize.toInt(), Bitmap.Config.ARGB_8888)
                    Canvas(nextBitmap).drawBitmap(bitmap, 0f, 0f, paint)
                    bitmap.recycle()
                    bitmap = nextBitmap
                    val rect = RectF(-size / 2f, -size / 2f, size / 2f, size / 2f)
                    canvas.save()
                    canvas.translate(it.x, it.y)
                    canvas.drawBitmap(bitmap, null, rect, Paint(Paint.DITHER_FLAG))
                    canvas.restore()
                    size -= step
                    pressure -= PRESSURE_UPDATE_STEP
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }
}
