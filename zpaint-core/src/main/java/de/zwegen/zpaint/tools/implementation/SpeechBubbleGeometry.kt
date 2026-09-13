package de.zwegen.zpaint.tools.implementation

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared drawing geometry for all speech-bubble bodies and tails.
 *
 * Keeping the body shape, tail attachment and tail size here prevents the
 * preview and committed bitmap from drifting apart as the tool evolves.
 */
internal object SpeechBubbleGeometry {
    const val TAIL_BASE_HALF_WIDTH = 24f

    private const val CLOUD_TAIL_ATTACHMENT_WIDTH = 0.50f
    private const val CLOUD_TAIL_ATTACHMENT_HEIGHT = 0.38f

    fun bodyPath(type: SpeechBubbleType, width: Float, height: Float): Path = when (type) {
        SpeechBubbleType.OVAL, SpeechBubbleType.THOUGHT -> Path().apply {
            addOval(RectF(-width / 2f, -height / 2f, width / 2f, height / 2f), Path.Direction.CW)
        }
        SpeechBubbleType.ROUNDED_RECTANGLE -> Path().apply {
            val rect = RectF(-width / 2f, -height / 2f, width / 2f, height / 2f)
            val corner = min(width, height) * 0.18f
            addRoundRect(rect, corner, corner, Path.Direction.CW)
        }
        SpeechBubbleType.CLOUD -> cloudBodyPath(width, height)
    }

    fun tailPath(type: SpeechBubbleType, width: Float, height: Float, tail: PointF, lineWidth: Float): Path {
        val attachment = tailAttachmentPoint(type, width, height, tail)
        val angle = atan2(tail.y, tail.x)
        val tangentX = -sin(angle) * TAIL_BASE_HALF_WIDTH
        val tangentY = cos(angle) * TAIL_BASE_HALF_WIDTH
        val directionX = cos(angle)
        val directionY = sin(angle)
        val overlap = max(lineWidth * 2f, TAIL_BASE_HALF_WIDTH * 0.9f)
        val startX = attachment.x + tangentX - directionX * overlap
        val startY = attachment.y + tangentY - directionY * overlap
        val endX = attachment.x - tangentX - directionX * overlap
        val endY = attachment.y - tangentY - directionY * overlap
        return Path().apply {
            moveTo(startX, startY)
            lineTo(tail.x, tail.y)
            lineTo(endX, endY)
            close()
        }
    }

    fun tailAttachmentPoint(type: SpeechBubbleType, width: Float, height: Float, tail: PointF): PointF {
        val angle = atan2(tail.y, tail.x)
        val attachmentWidth = if (type == SpeechBubbleType.CLOUD) width * CLOUD_TAIL_ATTACHMENT_WIDTH else width
        val attachmentHeight = if (type == SpeechBubbleType.CLOUD) height * CLOUD_TAIL_ATTACHMENT_HEIGHT else height
        return outlinePoint(attachmentWidth, attachmentHeight, angle, type != SpeechBubbleType.ROUNDED_RECTANGLE)
    }

    private fun cloudBodyPath(width: Float, height: Float): Path {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        return Path().apply {
            moveTo(-halfWidth * 0.621f, halfHeight * 0.435f)
            cubicTo(-halfWidth * 0.905f, halfHeight * 0.479f, -halfWidth * 0.918f, halfHeight * 0.058f, -halfWidth * 0.689f, -halfHeight * 0.073f)
            cubicTo(-halfWidth * 0.891f, -halfHeight * 0.363f, -halfWidth * 0.608f, -halfHeight * 0.740f, -halfWidth * 0.324f, -halfHeight * 0.609f)
            cubicTo(-halfWidth * 0.122f, -halfHeight * 0.928f, halfWidth * 0.270f, -halfHeight * 0.885f, halfWidth * 0.351f, -halfHeight * 0.580f)
            cubicTo(halfWidth * 0.662f, -halfHeight * 0.725f, halfWidth * 0.918f, -halfHeight * 0.348f, halfWidth * 0.716f, -halfHeight * 0.058f)
            cubicTo(halfWidth * 0.945f, halfHeight * 0.116f, halfWidth * 0.743f, halfHeight * 0.522f, halfWidth * 0.432f, halfHeight * 0.450f)
            cubicTo(halfWidth * 0.203f, halfHeight * 0.812f, -halfWidth * 0.203f, halfHeight * 0.783f, -halfWidth * 0.338f, halfHeight * 0.493f)
            cubicTo(-halfWidth * 0.446f, halfHeight * 0.624f, -halfWidth * 0.675f, halfHeight * 0.595f, -halfWidth * 0.621f, halfHeight * 0.435f)
            close()
        }
    }

    private fun outlinePoint(width: Float, height: Float, angle: Float, oval: Boolean): PointF {
        val cosine = cos(angle)
        val sine = sin(angle)
        if (oval) {
            val radiusX = width / 2f
            val radiusY = height / 2f
            val scale = 1f / kotlin.math.sqrt((cosine * cosine) / (radiusX * radiusX) + (sine * sine) / (radiusY * radiusY))
            return PointF(cosine * scale, sine * scale)
        }
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val scale = min(halfWidth / abs(cosine).coerceAtLeast(0.0001f), halfHeight / abs(sine).coerceAtLeast(0.0001f))
        return PointF(cosine * scale, sine * scale)
    }
}
