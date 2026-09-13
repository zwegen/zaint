package de.zwegen.zpaint.tools

import android.graphics.Paint
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/** Mutable paint state used by Zaint drawing tools and their previews. */
interface ToolPaint {
    var paint: Paint
    val previewPaint: Paint
    var color: Int
    var strokeWidth: Float
    var strokeCap: Paint.Cap
    val previewColor: Int
    val eraseXfermode: PorterDuffXfermode
    val checkeredShader: Shader?

    fun setAntialiasing()
}
