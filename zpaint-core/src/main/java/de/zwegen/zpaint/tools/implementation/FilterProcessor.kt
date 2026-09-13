/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.Color
import de.zwegen.zpaint.tools.gpu.GpuFilter
import de.zwegen.zpaint.tools.gpu.GpuFilterEngine
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object FilterProcessor {
    private data class PreviewSource(
        val bitmap: Bitmap,
        val scale: Float
    )

    fun apply(source: Bitmap, filterType: FilterType, value: Int): Bitmap {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        return try {
            when (filterType) {
                FilterType.INVERT -> GpuFilterEngine.apply(bitmap, GpuFilter.Invert)
                FilterType.BRIGHTNESS -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Brightness(value / COLOR_MAX_FLOAT))
                }
                FilterType.CONTRAST -> {
                    val factor = contrastFactor(value)
                    GpuFilterEngine.apply(bitmap, GpuFilter.Contrast(factor))
                }
                FilterType.BLUR -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Blur(value))
                }
                FilterType.BLOOM -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Bloom(value / PERCENT_FLOAT))
                }
                FilterType.MEDIAN -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Median(value))
                }
                FilterType.BILATERAL -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Bilateral(value))
                }
                FilterType.SATURATION -> {
                    val amount = 1f + (value / PERCENT_FLOAT)
                    GpuFilterEngine.apply(bitmap, GpuFilter.Saturation(amount))
                }
                FilterType.TEMPERATURE -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Temperature(value / PERCENT_FLOAT))
                }
                FilterType.HIGHLIGHTS -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Highlights(value / PERCENT_FLOAT))
                }
                FilterType.SHADOWS -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Shadows(value / PERCENT_FLOAT))
                }
                FilterType.SEPIA -> {
                    val strength = value / PERCENT_FLOAT
                    GpuFilterEngine.apply(bitmap, GpuFilter.Sepia(strength))
                }
                FilterType.SHARPEN -> {
                    val strength = (value.coerceIn(0, 100) / PERCENT_FLOAT) * SHARPEN_MAX_STRENGTH
                    GpuFilterEngine.apply(bitmap, GpuFilter.Sharpen(strength))
                }
                FilterType.VIGNETTE -> {
                    val strength = value.coerceIn(0, 100) / PERCENT_FLOAT
                    GpuFilterEngine.apply(bitmap, GpuFilter.Vignette(strength))
                }
                FilterType.CLARITY -> {
                    val strength = value.coerceIn(0, 100) / PERCENT_FLOAT
                    GpuFilterEngine.apply(bitmap, GpuFilter.Clarity(strength))
                }
                FilterType.EXPOSURE -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Exposure(value / PERCENT_FLOAT))
                }
                FilterType.VIBRANCE -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Vibrance(value / PERCENT_FLOAT))
                }
                FilterType.PIXEL -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Pixelate(value))
                }
                FilterType.HUE -> {
                    GpuFilterEngine.apply(bitmap, GpuFilter.Hue(value / HUE_RANGE_FLOAT))
                }
                // Splash needs both a selected colour and a tolerance, so it is applied through
                // applySplash rather than this generic one-value filter path.
                FilterType.SPLASH -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
                FilterType.CONTOURS -> ContourProcessor.extract(bitmap, value)
                FilterType.CONTOUR_ERASE -> ContourProcessor.hideDetectedLines(bitmap, value)
            }
        } catch (exception: RuntimeException) {
            // A GPU driver can reject a shader or texture although all filter values are valid.
            // Keep the layer unchanged in that case instead of letting a background preview or
            // the final command terminate the editor.
            source.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            bitmap.recycle()
        }
    }

    fun applyPreview(source: Bitmap, filterType: FilterType, value: Int): Bitmap {
        val previewSource = createPreviewSource(source)
        return try {
            val filteredPreview = apply(
                previewSource.bitmap,
                filterType,
                previewValueFor(filterType, value, previewSource.scale)
            )
            if (filteredPreview.width == source.width && filteredPreview.height == source.height) {
                filteredPreview
            } else {
                Bitmap.createScaledBitmap(filteredPreview, source.width, source.height, true).also {
                    filteredPreview.recycle()
                }
            }
        } finally {
            if (previewSource.bitmap !== source) {
                previewSource.bitmap.recycle()
            }
        }
    }

    fun applyBlurArea(source: Bitmap, value: Int, centerX: Float, centerY: Float, radius: Float): Bitmap =
        applyArea(source, FilterType.BLUR, value, FilterArea.circle(centerX, centerY, radius))

    fun applyArea(
        source: Bitmap,
        filterType: FilterType,
        value: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Bitmap = applyArea(source, filterType, value, FilterArea.circle(centerX, centerY, radius))

    fun applyArea(
        source: Bitmap,
        filterType: FilterType,
        value: Int,
        area: FilterArea
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        if (value == 0) {
            return output
        }
        val filtered = apply(source, filterType, value)
        val boundedArea = area.normalized(MIN_AREA_RADIUS)
        val left = max(0, floor(boundedArea.centerX - boundedArea.boundingHalfWidth).toInt())
        val top = max(0, floor(boundedArea.centerY - boundedArea.boundingHalfHeight).toInt())
        val right = min(source.width - 1, ceil(boundedArea.centerX + boundedArea.boundingHalfWidth).toInt())
        val bottom = min(source.height - 1, ceil(boundedArea.centerY + boundedArea.boundingHalfHeight).toInt())
        val featherWidth = areaFeatherWidth(filterType, value, boundedArea.maximumCornerRadius)
        for (y in top..bottom) {
            for (x in left..right) {
                val edgeDistance = boundedArea.edgeDistance(x + HALF_PIXEL, y + HALF_PIXEL)
                if (edgeDistance <= NO_ALPHA) {
                    continue
                }
                val alpha = areaEffectAlpha(edgeDistance, featherWidth)
                if (alpha >= FULL_ALPHA) {
                    output.setPixel(x, y, filtered.getPixel(x, y))
                } else if (alpha > NO_ALPHA) {
                    output.setPixel(x, y, blend(source.getPixel(x, y), filtered.getPixel(x, y), alpha))
                }
            }
        }
        filtered.recycle()
        return output
    }

    fun applyRecolor(source: Bitmap, sourceColor: Int, targetColor: Int, tolerance: Int): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = output.readPixels()
        val targetHsv = FloatArray(HSV_COMPONENT_COUNT)
        Color.colorToHSV(targetColor, targetHsv)
        val threshold = recolorThreshold(tolerance)
        for (index in pixels.indices) {
            val color = pixels[index]
            if (Color.alpha(color) == 0 || colorDistance(color, sourceColor) > threshold) {
                continue
            }
            pixels[index] = recolorKeepingValue(color, targetHsv)
        }
        output.writePixels(pixels)
        return output
    }

    /** Keeps the selected colour colourful and fades similar colours softly into grey. */
    fun applySplash(
        source: Bitmap,
        selectedColor: Int,
        tolerance: Int
    ): Bitmap = applySplash(source, intArrayOf(selectedColor), tolerance)

    /** Keeps every selected colour colourful and fades all other colours softly into grey. */
    fun applySplash(
        source: Bitmap,
        selectedColors: IntArray,
        tolerance: Int
    ): Bitmap = applySplash(source, selectedColors, IntArray(selectedColors.size) { tolerance })

    fun applySplash(
        source: Bitmap,
        selectedColors: IntArray,
        tolerances: IntArray
    ): Bitmap {
        require(selectedColors.isNotEmpty()) { "At least one Splash colour is required" }
        require(selectedColors.size == tolerances.size) { "Each Splash colour needs a tolerance" }
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val pixels = output.readPixels()
            val selectedHsvColors = selectedColors.map { selectedColor ->
                FloatArray(HSV_COMPONENT_COUNT).also { Color.colorToHSV(selectedColor, it) }
            }
            for (index in pixels.indices) {
                val color = pixels[index]
                if (Color.alpha(color) == 0) {
                    continue
                }
                val colorRetention = selectedHsvColors.indices.maxOf { colorIndex ->
                    splashColorRetention(splashColorDistance(color, selectedHsvColors[colorIndex]), tolerances[colorIndex])
                }
                if (colorRetention >= FULL_ALPHA) continue
                val grey = luminance(color)
                val greyColor = Color.argb(Color.alpha(color), grey, grey, grey)
                pixels[index] = if (colorRetention <= NO_ALPHA) {
                    greyColor
                } else {
                    blend(greyColor, color, colorRetention)
                }
            }
            output.writePixels(pixels)
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    fun applySplashPreview(
        source: Bitmap,
        selectedColor: Int,
        tolerance: Int
    ): Bitmap = applySplashPreview(source, intArrayOf(selectedColor), tolerance)

    fun applySplashPreview(
        source: Bitmap,
        selectedColors: IntArray,
        tolerance: Int
    ): Bitmap = applySplashPreview(source, selectedColors, IntArray(selectedColors.size) { tolerance })

    fun applySplashPreview(
        source: Bitmap,
        selectedColors: IntArray,
        tolerances: IntArray
    ): Bitmap {
        val previewSource = createPreviewSource(source)
        return try {
            val filteredPreview = applySplash(previewSource.bitmap, selectedColors, tolerances)
            if (filteredPreview.width == source.width && filteredPreview.height == source.height) {
                filteredPreview
            } else {
                Bitmap.createScaledBitmap(filteredPreview, source.width, source.height, true).also {
                    filteredPreview.recycle()
                }
            }
        } finally {
            if (previewSource.bitmap !== source) {
                previewSource.bitmap.recycle()
            }
        }
    }

    fun applySplashArea(
        source: Bitmap,
        selectedColor: Int,
        tolerance: Int,
        area: FilterArea
    ): Bitmap = applySplashArea(source, intArrayOf(selectedColor), tolerance, area)

    fun applySplashArea(
        source: Bitmap,
        selectedColors: IntArray,
        tolerance: Int,
        area: FilterArea
    ): Bitmap = applySplashArea(source, selectedColors, IntArray(selectedColors.size) { tolerance }, area)

    fun applySplashArea(
        source: Bitmap,
        selectedColors: IntArray,
        tolerances: IntArray,
        area: FilterArea
    ): Bitmap = applySplashAreas(source, selectedColors, tolerances, List(selectedColors.size) { area })

    /**
     * Keeps each selected Splash colour only inside its own area. Areas may overlap; in that
     * case the strongest matching colour is kept at each pixel.
     */
    fun applySplashAreas(
        source: Bitmap,
        selectedColors: IntArray,
        tolerances: IntArray,
        areas: List<FilterArea>
    ): Bitmap {
        require(selectedColors.isNotEmpty()) { "At least one Splash colour is required" }
        require(selectedColors.size == tolerances.size) { "Each Splash colour needs a tolerance" }
        require(selectedColors.size == areas.size) { "Each Splash colour needs an area" }

        // In area mode Splash starts with a greyscale image. Only a colour inside its own area
        // may restore colour; leaving the rest as the original image would not be a Splash.
        val output = grayscaleCopy(source)
        try {
            val sourcePixels = source.readPixels()
            val outputPixels = output.readPixels()
            val selectedHsvColors = selectedColors.map { selectedColor ->
                FloatArray(HSV_COMPONENT_COUNT).also { Color.colorToHSV(selectedColor, it) }
            }
            val boundedAreas = areas.map { it.normalized(MIN_AREA_RADIUS) }
            val featherWidths = boundedAreas.indices.map { index ->
                areaFeatherWidth(FilterType.SPLASH, tolerances[index], boundedAreas[index].maximumCornerRadius)
            }
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    val pixelIndex = y * source.width + x
                    val color = sourcePixels[pixelIndex]
                    if (Color.alpha(color) == 0) continue
                    var retention = NO_ALPHA
                    for (colorIndex in selectedHsvColors.indices) {
                        val area = boundedAreas[colorIndex]
                        val edgeDistance = area.edgeDistance(x + HALF_PIXEL, y + HALF_PIXEL)
                        if (edgeDistance <= NO_ALPHA) continue
                        val areaAlpha = areaEffectAlpha(edgeDistance, featherWidths[colorIndex])
                        if (areaAlpha <= NO_ALPHA) continue
                        val colorAlpha = splashColorRetention(
                            splashColorDistance(color, selectedHsvColors[colorIndex]),
                            tolerances[colorIndex]
                        )
                        retention = max(retention, areaAlpha * colorAlpha)
                    }
                    if (retention > NO_ALPHA) {
                        outputPixels[pixelIndex] = blend(outputPixels[pixelIndex], color, retention)
                    }
                }
            }
            output.writePixels(outputPixels)
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    private fun grayscaleCopy(source: Bitmap): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val pixels = output.readPixels()
            for (index in pixels.indices) {
                val color = pixels[index]
                val grey = luminance(color)
                pixels[index] = Color.argb(Color.alpha(color), grey, grey, grey)
            }
            output.writePixels(pixels)
            return output
        } catch (error: Throwable) {
            output.recycle()
            throw error
        }
    }

    fun applySplashAreaPreview(
        source: Bitmap,
        selectedColor: Int,
        tolerance: Int,
        area: FilterArea
    ): Bitmap = applySplashAreaPreview(source, intArrayOf(selectedColor), tolerance, area)

    fun applySplashAreaPreview(
        source: Bitmap,
        selectedColors: IntArray,
        tolerance: Int,
        area: FilterArea
    ): Bitmap = applySplashAreaPreview(source, selectedColors, IntArray(selectedColors.size) { tolerance }, area)

    fun applySplashAreaPreview(
        source: Bitmap,
        selectedColors: IntArray,
        tolerances: IntArray,
        area: FilterArea
    ): Bitmap = applySplashAreasPreview(source, selectedColors, tolerances, List(selectedColors.size) { area })

    fun applySplashAreasPreview(
        source: Bitmap,
        selectedColors: IntArray,
        tolerances: IntArray,
        areas: List<FilterArea>
    ): Bitmap {
        val previewSource = createPreviewSource(source)
        val scaledAreas = areas.map { it.scaled(previewSource.scale) }
        return try {
            val filteredPreview = applySplashAreas(
                previewSource.bitmap,
                selectedColors,
                tolerances,
                scaledAreas
            )
            if (filteredPreview.width == source.width && filteredPreview.height == source.height) {
                filteredPreview
            } else {
                Bitmap.createScaledBitmap(filteredPreview, source.width, source.height, true).also {
                    filteredPreview.recycle()
                }
            }
        } finally {
            if (previewSource.bitmap !== source) {
                previewSource.bitmap.recycle()
            }
        }
    }

    fun applyRecolorPreview(source: Bitmap, sourceColor: Int, targetColor: Int, tolerance: Int): Bitmap {
        val previewSource = createPreviewSource(source)
        val filteredPreview = applyRecolor(previewSource.bitmap, sourceColor, targetColor, tolerance)
        if (previewSource.bitmap !== source) {
            previewSource.bitmap.recycle()
        }
        if (filteredPreview.width == source.width && filteredPreview.height == source.height) {
            return filteredPreview
        }
        return Bitmap.createScaledBitmap(filteredPreview, source.width, source.height, true).also {
            filteredPreview.recycle()
        }
    }

    fun applyRecolorArea(
        source: Bitmap,
        sourceColor: Int,
        targetColor: Int,
        tolerance: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val filtered = applyRecolor(source, sourceColor, targetColor, tolerance)
        val boundedRadius = radius.coerceAtLeast(MIN_AREA_RADIUS)
        val left = max(0, floor(centerX - boundedRadius).toInt())
        val top = max(0, floor(centerY - boundedRadius).toInt())
        val right = min(source.width - 1, ceil(centerX + boundedRadius).toInt())
        val bottom = min(source.height - 1, ceil(centerY + boundedRadius).toInt())
        val featherWidth = (boundedRadius * RECOLOR_AREA_FEATHER_RADIUS_FACTOR).coerceAtMost(boundedRadius)
        for (y in top..bottom) {
            for (x in left..right) {
                val distance = distanceToCenter(x, y, centerX, centerY)
                if (distance >= boundedRadius) {
                    continue
                }
                val alpha = areaEffectAlpha(distance, boundedRadius, featherWidth)
                if (alpha >= FULL_ALPHA) {
                    output.setPixel(x, y, filtered.getPixel(x, y))
                } else if (alpha > NO_ALPHA) {
                    output.setPixel(x, y, blend(source.getPixel(x, y), filtered.getPixel(x, y), alpha))
                }
            }
        }
        filtered.recycle()
        return output
    }

    fun applyRecolorAreaPreview(
        source: Bitmap,
        sourceColor: Int,
        targetColor: Int,
        tolerance: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Bitmap {
        val previewSource = createPreviewSource(source)
        val filteredPreview = applyRecolorArea(
            previewSource.bitmap,
            sourceColor,
            targetColor,
            tolerance,
            centerX * previewSource.scale,
            centerY * previewSource.scale,
            radius * previewSource.scale
        )
        if (previewSource.bitmap !== source) {
            previewSource.bitmap.recycle()
        }
        if (filteredPreview.width == source.width && filteredPreview.height == source.height) {
            return filteredPreview
        }
        return Bitmap.createScaledBitmap(filteredPreview, source.width, source.height, true).also {
            filteredPreview.recycle()
        }
    }

    fun applyBlurAreaPreview(source: Bitmap, value: Int, centerX: Float, centerY: Float, radius: Float): Bitmap {
        return applyAreaPreview(source, FilterType.BLUR, value, centerX, centerY, radius)
    }

    fun applyAreaPreview(
        source: Bitmap,
        filterType: FilterType,
        value: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Bitmap = applyAreaPreview(source, filterType, value, FilterArea.circle(centerX, centerY, radius))

    fun applyAreaPreview(
        source: Bitmap,
        filterType: FilterType,
        value: Int,
        area: FilterArea
    ): Bitmap {
        val previewSource = createPreviewSource(source)
        return try {
            val filteredPreview = applyArea(
                previewSource.bitmap,
                filterType,
                previewValueFor(filterType, value, previewSource.scale),
                area.scaled(previewSource.scale)
            )
            if (filteredPreview.width == source.width && filteredPreview.height == source.height) {
                filteredPreview
            } else {
                Bitmap.createScaledBitmap(filteredPreview, source.width, source.height, true).also {
                    filteredPreview.recycle()
                }
            }
        } finally {
            if (previewSource.bitmap !== source) {
                previewSource.bitmap.recycle()
            }
        }
    }

    private fun createPreviewSource(source: Bitmap): PreviewSource {
        val largestSide = max(source.width, source.height)
        if (largestSide <= PREVIEW_MAX_SIDE) {
            return PreviewSource(source, FULL_ALPHA)
        }
        val scale = PREVIEW_MAX_SIDE / largestSide.toFloat()
        val previewWidth = max(1, (source.width * scale).roundToInt())
        val previewHeight = max(1, (source.height * scale).roundToInt())
        return PreviewSource(
            Bitmap.createScaledBitmap(source, previewWidth, previewHeight, true),
            scale
        )
    }

    private fun previewValueFor(filterType: FilterType, value: Int, scale: Float): Int =
        when (filterType) {
            FilterType.BLUR -> (value * scale).roundToInt().coerceIn(0, BLUR_MAX_RADIUS)
            FilterType.PIXEL -> (value * scale).roundToInt().coerceIn(PIXEL_SIZE_MIN, PIXEL_SIZE_MAX)
            else -> value
        }

    private fun invert(bitmap: Bitmap) {
        val pixels = bitmap.readPixels()
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = Color.argb(
                Color.alpha(color),
                255 - Color.red(color),
                255 - Color.green(color),
                255 - Color.blue(color)
            )
        }
        bitmap.writePixels(pixels)
    }

    private fun brightness(bitmap: Bitmap, value: Int) {
        val pixels = bitmap.readPixels()
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = Color.argb(
                Color.alpha(color),
                clamp(Color.red(color) + value),
                clamp(Color.green(color) + value),
                clamp(Color.blue(color) + value)
            )
        }
        bitmap.writePixels(pixels)
    }

    private fun contrast(bitmap: Bitmap, value: Int) {
        val factor = contrastFactor(value)
        val pixels = bitmap.readPixels()
        for (index in pixels.indices) {
            val color = pixels[index]
            pixels[index] = Color.argb(
                Color.alpha(color),
                clamp((factor * (Color.red(color) - 128f) + 128f).toInt()),
                clamp((factor * (Color.green(color) - 128f) + 128f).toInt()),
                clamp((factor * (Color.blue(color) - 128f) + 128f).toInt())
            )
        }
        bitmap.writePixels(pixels)
    }

    private fun saturation(bitmap: Bitmap, value: Int) {
        val amount = 1f + (value / PERCENT_FLOAT)
        val pixels = bitmap.readPixels()
        for (index in pixels.indices) {
            val color = pixels[index]
            val gray = luminance(color)
            pixels[index] = Color.argb(
                Color.alpha(color),
                clamp((gray + (Color.red(color) - gray) * amount).toInt()),
                clamp((gray + (Color.green(color) - gray) * amount).toInt()),
                clamp((gray + (Color.blue(color) - gray) * amount).toInt())
            )
        }
        bitmap.writePixels(pixels)
    }

    private fun sepia(bitmap: Bitmap, value: Int) {
        val strength = value / PERCENT_FLOAT
        val pixels = bitmap.readPixels()
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            val sepiaRed = clamp((red * SEPIA_RED_RED + green * SEPIA_RED_GREEN + blue * SEPIA_RED_BLUE).toInt())
            val sepiaGreen = clamp((red * SEPIA_GREEN_RED + green * SEPIA_GREEN_GREEN + blue * SEPIA_GREEN_BLUE).toInt())
            val sepiaBlue = clamp((red * SEPIA_BLUE_RED + green * SEPIA_BLUE_GREEN + blue * SEPIA_BLUE_BLUE).toInt())
            pixels[index] = Color.argb(
                Color.alpha(color),
                clamp((red + (sepiaRed - red) * strength).toInt()),
                clamp((green + (sepiaGreen - green) * strength).toInt()),
                clamp((blue + (sepiaBlue - blue) * strength).toInt())
            )
        }
        bitmap.writePixels(pixels)
    }

    private fun blur(bitmap: Bitmap, radius: Int) {
        if (radius <= 0) {
            return
        }
        val width = bitmap.width
        val height = bitmap.height
        val source = bitmap.readPixels()
        val temp = IntArray(source.size)
        val output = IntArray(source.size)
        boxBlurHorizontal(source, temp, width, height, radius)
        boxBlurVertical(temp, output, width, height, radius)
        bitmap.writePixels(output)
    }

    private fun boxBlurHorizontal(source: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                val start = max(0, x - radius)
                val end = min(width - 1, x + radius)
                for (sampleX in start..end) {
                    val color = source[row + sampleX]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count++
                }
                output[row + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
            }
        }
    }

    private fun boxBlurVertical(source: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                val start = max(0, y - radius)
                val end = min(height - 1, y + radius)
                for (sampleY in start..end) {
                    val color = source[sampleY * width + x]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    count++
                }
                output[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
            }
        }
    }

    private fun recolorKeepingValue(color: Int, targetHsv: FloatArray): Int {
        val pixelHsv = FloatArray(HSV_COMPONENT_COUNT)
        Color.colorToHSV(color, pixelHsv)
        pixelHsv[HSV_HUE] = targetHsv[HSV_HUE]
        pixelHsv[HSV_SATURATION] = targetHsv[HSV_SATURATION]
        return Color.HSVToColor(Color.alpha(color), pixelHsv)
    }

    private fun colorDistance(first: Int, second: Int): Float {
        val red = Color.red(first) - Color.red(second)
        val green = Color.green(first) - Color.green(second)
        val blue = Color.blue(first) - Color.blue(second)
        return sqrt((red * red + green * green + blue * blue).toFloat())
    }

    private fun recolorThreshold(tolerance: Int): Float =
        MAX_RECOLOR_DISTANCE * tolerance.coerceIn(0, 100) / 100f

    /**
     * Splash compares the actual colour (hue) and colour strength (saturation), not RGB
     * channel distances. Brightness is deliberately ignored, so light and dark versions of
     * the selected colour remain colourful while a different hue, such as green instead of
     * brown, turns grey.
     */
    private fun splashColorDistance(color: Int, selectedHsv: FloatArray): Float {
        val pixelHsv = FloatArray(HSV_COMPONENT_COUNT)
        Color.colorToHSV(color, pixelHsv)
        val hueDistance = hueDistance(pixelHsv[HSV_HUE], selectedHsv[HSV_HUE])
        val normalizedHueDistance = hueDistance * PERCENT_MAX / SPLASH_HUE_RANGE
        val saturationDistance = abs(pixelHsv[HSV_SATURATION] - selectedHsv[HSV_SATURATION]) *
            PERCENT_MAX * SPLASH_SATURATION_WEIGHT
        return max(normalizedHueDistance, saturationDistance)
    }

    private fun hueDistance(firstHue: Float, secondHue: Float): Float {
        val difference = abs(firstHue - secondHue)
        return min(difference, HUE_FULL_CIRCLE - difference)
    }

    /** The last 35% of the selected tolerance fades smoothly to grey. */
    private fun splashColorRetention(distance: Float, tolerance: Int): Float {
        val outerThreshold = tolerance.coerceIn(0, PERCENT_MAX).toFloat()
        if (outerThreshold <= 0f) return if (distance <= 0f) FULL_ALPHA else NO_ALPHA
        val innerThreshold = outerThreshold * SPLASH_SOLID_COLOR_PORTION
        if (distance <= innerThreshold) return FULL_ALPHA
        if (distance >= outerThreshold) return NO_ALPHA
        val progress = (outerThreshold - distance) / (outerThreshold - innerThreshold)
        return smoothStep(progress)
    }

    private fun Bitmap.readPixels(): IntArray =
        IntArray(checkedPixelCount()).also { getPixels(it, 0, width, 0, 0, width, height) }

    private fun Bitmap.writePixels(pixels: IntArray) {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun Bitmap.checkedPixelCount(): Int {
        val pixelCount = width.toLong() * height.toLong()
        require(pixelCount <= Int.MAX_VALUE) { "Bitmap is too large for filter processing" }
        return pixelCount.toInt()
    }

    private fun contrastFactor(value: Int): Float =
        (259f * (value + COLOR_MAX_FLOAT)) / (COLOR_MAX_FLOAT * (259f - value))

    private fun luminance(color: Int): Int =
        (Color.red(color) * LUMINANCE_RED +
            Color.green(color) * LUMINANCE_GREEN +
            Color.blue(color) * LUMINANCE_BLUE).toInt()

    private fun clamp(value: Int): Int = value.coerceIn(0, 255)

    private fun distanceToCenter(x: Int, y: Int, centerX: Float, centerY: Float): Float {
        val distanceX = x + HALF_PIXEL - centerX
        val distanceY = y + HALF_PIXEL - centerY
        return sqrt(distanceX * distanceX + distanceY * distanceY)
    }

    private fun areaEffectAlpha(edgeDistance: Float, featherWidth: Float): Float {
        val boundedFeatherWidth = featherWidth.coerceAtLeast(NO_ALPHA)
        if (edgeDistance >= boundedFeatherWidth) return FULL_ALPHA
        val alpha = (edgeDistance / boundedFeatherWidth).coerceIn(NO_ALPHA, FULL_ALPHA)
        return smoothStep(alpha)
    }

    private fun areaEffectAlpha(distance: Float, radius: Float, featherWidth: Float): Float {
        if (distance >= radius) return NO_ALPHA
        return areaEffectAlpha(radius - distance, featherWidth.coerceAtMost(radius))
    }

    private fun areaFeatherWidth(filterType: FilterType, value: Int, radius: Float): Float {
        val featherWidth = if (filterType == FilterType.PIXEL) {
            val pixelFeatherWidth = (radius * PIXEL_AREA_FEATHER_RADIUS_FACTOR)
                .coerceIn(AREA_FEATHER_MIN, AREA_FEATHER_MAX)
            max(pixelFeatherWidth, value.coerceAtLeast(PIXEL_SIZE_MIN) * AREA_FEATHER_PIXEL_SIZE_FACTOR)
        } else {
            radius * AREA_FEATHER_RADIUS_FACTOR
        }
        return featherWidth.coerceAtMost(radius)
    }

    private fun blend(original: Int, filtered: Int, alpha: Float): Int {
        val inverseAlpha = FULL_ALPHA - alpha
        return Color.argb(
            blendChannel(Color.alpha(original), Color.alpha(filtered), inverseAlpha, alpha),
            blendChannel(Color.red(original), Color.red(filtered), inverseAlpha, alpha),
            blendChannel(Color.green(original), Color.green(filtered), inverseAlpha, alpha),
            blendChannel(Color.blue(original), Color.blue(filtered), inverseAlpha, alpha)
        )
    }

    private fun blendChannel(original: Int, filtered: Int, inverseAlpha: Float, alpha: Float): Int =
        (original * inverseAlpha + filtered * alpha).toInt().coerceIn(0, 255)

    private fun smoothStep(value: Float): Float =
        value * value * (SMOOTH_STEP_FACTOR - SMOOTH_STEP_DOUBLE * value)

    private const val COLOR_MAX = 255
    private const val COLOR_MAX_FLOAT = 255f
    private const val PERCENT_MAX = 100
    private const val PERCENT_FLOAT = 100f
    private const val HUE_RANGE_FLOAT = 200f
    private const val PREVIEW_MAX_SIDE = 1400
    private const val BLUR_MAX_RADIUS = 25
    private const val MIN_AREA_RADIUS = 8f
    private const val HALF_PIXEL = 0.5f
    private const val FULL_ALPHA = 1f
    private const val NO_ALPHA = 0f
    private const val AREA_FEATHER_RADIUS_FACTOR = 0.58f
    private const val RECOLOR_AREA_FEATHER_RADIUS_FACTOR = 0.10f
    private const val PIXEL_AREA_FEATHER_RADIUS_FACTOR = 0.12f
    private const val AREA_FEATHER_PIXEL_SIZE_FACTOR = 1.2f
    private const val AREA_FEATHER_MIN = 8f
    private const val AREA_FEATHER_MAX = 50f
    private const val SMOOTH_STEP_FACTOR = 3f
    private const val SMOOTH_STEP_DOUBLE = 2f
    private const val SHARPEN_MAX_STRENGTH = 0.75f
    private const val LUMINANCE_RED = 0.299f
    private const val LUMINANCE_GREEN = 0.587f
    private const val LUMINANCE_BLUE = 0.114f
    private const val SEPIA_RED_RED = 0.393f
    private const val SEPIA_RED_GREEN = 0.769f
    private const val SEPIA_RED_BLUE = 0.189f
    private const val SEPIA_GREEN_RED = 0.349f
    private const val SEPIA_GREEN_GREEN = 0.686f
    private const val SEPIA_GREEN_BLUE = 0.168f
    private const val SEPIA_BLUE_RED = 0.272f
    private const val SEPIA_BLUE_GREEN = 0.534f
    private const val SEPIA_BLUE_BLUE = 0.131f
    private const val PIXEL_SIZE_MIN = 1
    private const val PIXEL_SIZE_MAX = 50
    private const val HSV_COMPONENT_COUNT = 3
    private const val HSV_HUE = 0
    private const val HSV_SATURATION = 1
    private const val MAX_RECOLOR_DISTANCE = 441.67294f
    private const val SPLASH_SOLID_COLOR_PORTION = 0.65f
    private const val HUE_FULL_CIRCLE = 360f
    private const val SPLASH_HUE_RANGE = 120f
    private const val SPLASH_SATURATION_WEIGHT = 0.5f
}
