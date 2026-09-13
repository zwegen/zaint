/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.gpu


sealed class GpuFilter {
    object Invert : GpuFilter()
    data class Brightness(val offset: Float) : GpuFilter()
    data class Contrast(val factor: Float) : GpuFilter()
    data class Saturation(val amount: Float) : GpuFilter()
    data class Temperature(val amount: Float) : GpuFilter()
    data class Highlights(val amount: Float) : GpuFilter()
    data class Shadows(val amount: Float) : GpuFilter()
    data class Sepia(val strength: Float) : GpuFilter()
    data class Sharpen(val strength: Float) : GpuFilter()
    data class Blur(val radius: Int) : GpuFilter()
    data class Bloom(val strength: Float) : GpuFilter()
    data class Median(val strength: Int) : GpuFilter()
    data class Bilateral(val strength: Int) : GpuFilter()
    data class Vignette(val strength: Float) : GpuFilter()
    data class Clarity(val strength: Float) : GpuFilter()
    data class Exposure(val amount: Float) : GpuFilter()
    data class Vibrance(val amount: Float) : GpuFilter()
    data class Pixelate(val blockSize: Int) : GpuFilter()
    data class Hue(val shift: Float) : GpuFilter()
}
