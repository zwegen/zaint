package de.zwegen.zpaint.tools

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.graphics.Point
import android.graphics.PointF
import android.os.Bundle

/** Contract implemented by every interactive Zaint drawing tool. */
interface Tool {
    val toolType: ZaintToolKind

    val drawPaint: Paint

    var drawTime: Long

    fun handToolMode(): Boolean

    fun handleDown(coordinate: PointF?): Boolean

    fun handleMove(coordinate: PointF?, shouldAnimate: Boolean = false): Boolean

    fun handleUp(coordinate: PointF?): Boolean

    fun changePaintColor(color: Int, invalidate: Boolean = true)

    fun changePaintStrokeWidth(strokeWidth: Int)

    fun changePaintStrokeCap(cap: Cap)

    fun draw(canvas: Canvas)

    fun resetInternalState(stateChange: StateChange)

    fun getAutoScrollDirection(
        pointX: Float,
        pointY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Point

    fun handleUpAnimations(coordinate: PointF?)
    fun handleDownAnimations(coordinate: PointF?)
    fun onSaveInstanceState(bundle: Bundle?)

    fun onRestoreInstanceState(bundle: Bundle?)

    /** Explains why a tool must discard transient interaction state. */
    enum class StateChange {
        ALL, RESET_INTERNAL_STATE, NEW_IMAGE_LOADED, MOVE_CANCELED
    }

    fun toolPositionCoordinates(coordinate: PointF): PointF
}
