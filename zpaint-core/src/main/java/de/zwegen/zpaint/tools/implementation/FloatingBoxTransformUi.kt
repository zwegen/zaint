package de.zwegen.zpaint.tools.implementation

import android.graphics.Canvas
import android.graphics.Color
import android.util.DisplayMetrics
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Workspace

/** Exact geometry and hit areas of the established Icon/Import transform UI. */
class FloatingBoxTransformUi(
    private val contextCallback: ContextCallback,
    private val workspace: Workspace
) {
    private val interactionResolver = FloatingBoxInteractionResolver()
    private val arcPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val arrowPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val arcPath = Path()
    private val arrowPath = Path()
    private val arcBounds = RectF()

    fun resolve(
        pointX: Float,
        pointY: Float,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        rotationEnabled: Boolean
    ): FloatingBoxInteraction = interactionResolver.resolve(
        pointX,
        pointY,
        centerX,
        centerY,
        width,
        height,
        rotation,
        resizeMargin(),
        rotationEnabled,
        rotationSymbolDistance()
    )

    fun drawResizeMarkers(
        canvas: Canvas,
        width: Float,
        height: Float,
        shrinking: Int,
        paint: Paint
    ) = FloatingBoxTransformControls.drawResizeMarkers(
        canvas,
        width,
        height,
        strokeWidth(),
        shrinking,
        paint
    )

    fun drawRotationArrows(canvas: Canvas, width: Float, height: Float) =
        FloatingBoxTransformControls.drawRotationArrows(
            canvas,
            width,
            height,
            size(densitySpecificSize(2).toFloat()),
            size(densitySpecificSize(8).toFloat()),
            size(densitySpecificSize(3).toFloat()),
            size(densitySpecificSize(3).toFloat()),
            arcPaint,
            arrowPaint,
            arcPath,
            arrowPath,
            arcBounds
        )

    private fun resizeMargin(): Float = size(DEFAULT_BOX_RESIZE_MARGIN.toFloat())

    private fun rotationSymbolDistance(): Float = size(20f) * 2f

    private fun strokeWidth(): Float =
        (3f * contextCallback.displayMetrics.density / workspace.scale).coerceIn(1f, 8f)

    private fun densitySpecificSize(value: Int): Int {
        val baseDensity = DisplayMetrics.DENSITY_MEDIUM
        val density = maxOf(baseDensity, contextCallback.displayMetrics.densityDpi)
        return value * density / baseDensity
    }

    private fun size(value: Float): Float =
        value * contextCallback.displayMetrics.density / workspace.scale
}
