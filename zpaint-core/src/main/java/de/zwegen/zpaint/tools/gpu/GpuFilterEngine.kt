/*
 * ZPaint: An image manipulation application for Android.
 */
package de.zwegen.zpaint.tools.gpu

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object GpuFilterEngine {
    private var hasValidatedPipeline = false

    @Synchronized
    fun apply(source: Bitmap, filter: GpuFilter): Bitmap {
        require(source.config == Bitmap.Config.ARGB_8888) { "GpuFilterEngine requires ARGB_8888 bitmaps" }
        require(source.width > 0 && source.height > 0) { "GpuFilterEngine requires a non-empty bitmap" }

        if (filter is GpuFilter.Blur) {
            return applyBlur(source, filter.radius)
        }

        return applyOnGpu(source, filter)
    }

    private fun applyOnGpu(source: Bitmap, filter: GpuFilter): Bitmap {
        val session = EglSession(source.width, source.height)
        return try {
            session.makeCurrent()
            checkTextureSize(source.width, source.height)
            validatePipelineOnce()
            render(source, filter)
        } finally {
            session.release()
        }
    }

    /**
     * Preserve the established blur appearance for normal canvas sizes. Only a large source
     * together with a strong blur is processed on a smaller working bitmap, where that saves a
     * substantial amount of GPU memory.
     */
    private fun applyBlur(source: Bitmap, requestedRadius: Int): Bitmap {
        val radius = requestedRadius.coerceIn(0, BLUR_MAX_RADIUS)
        if (radius == 0) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val needsScaledWorkingBitmap = radius > BLUR_SCALED_MAX_RADIUS &&
            max(source.width, source.height) > BLUR_FULL_RES_MAX_SIDE
        if (!needsScaledWorkingBitmap) {
            return applyOnGpu(source, GpuFilter.Blur(radius))
        }

        val scaleFactor = ceil(radius / BLUR_SCALED_MAX_RADIUS.toFloat()).toInt()
        val scaledWidth = max(1, (source.width / scaleFactor.toFloat()).roundToInt())
        val scaledHeight = max(1, (source.height / scaleFactor.toFloat()).roundToInt())
        val scaledSource = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        return try {
            val scaledRadius = (radius / scaleFactor.toFloat())
                .roundToInt()
                .coerceIn(1, BLUR_SCALED_MAX_RADIUS)
            val blurred = applyOnGpu(scaledSource, GpuFilter.Blur(scaledRadius))
            try {
                Bitmap.createScaledBitmap(blurred, source.width, source.height, true)
            } finally {
                blurred.recycle()
            }
        } finally {
            scaledSource.recycle()
        }
    }

    @Synchronized
    fun createGradient(
        width: Int,
        height: Int,
        startColor: Int,
        endColor: Int,
        direction: FillGradientDirection
    ): Bitmap {
        require(width > 0 && height > 0) { "GpuFilterEngine requires a non-empty gradient" }

        val session = EglSession(width, height)
        return try {
            session.makeCurrent()
            checkTextureSize(width, height)
            renderGradient(width, height, startColor, endColor, direction)
        } finally {
            session.release()
        }
    }

    private fun validatePipelineOnce() {
        if (hasValidatedPipeline) {
            return
        }

        val testBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val inputPixels = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFF0000.toInt(),
            0xFF336699.toInt()
        )
        val expectedPixels = inputPixels.map { color ->
            val alpha = color and 0xFF000000.toInt()
            val red = 0xFF - ((color shr RED_SHIFT) and BYTE_MASK)
            val green = 0xFF - ((color shr GREEN_SHIFT) and BYTE_MASK)
            val blue = 0xFF - (color and BYTE_MASK)
            alpha or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }.toIntArray()
        testBitmap.setPixels(inputPixels, 0, 2, 0, 0, 2, 2)

        val result = renderSinglePass(testBitmap, GpuFilter.Invert)
        val actualPixels = IntArray(expectedPixels.size)
        result.getPixels(actualPixels, 0, 2, 0, 0, 2, 2)
        result.recycle()
        testBitmap.recycle()

        check(actualPixels.contentEquals(expectedPixels)) {
            "GPU self-test failed. expected=${expectedPixels.joinToString()} actual=${actualPixels.joinToString()}"
        }
        hasValidatedPipeline = true
    }

    private fun render(source: Bitmap, filter: GpuFilter): Bitmap =
        when (filter) {
            is GpuFilter.Blur -> renderBlur(source, filter.radius)
            else -> renderSinglePass(source, filter)
        }

    private fun renderSinglePass(source: Bitmap, filter: GpuFilter): Bitmap {
        val program = createProgram(VERTEX_SHADER, fragmentShader(filter))
        val inputTexture = createInputTexture(source)
        val outputTexture = createOutputTexture(source.width, source.height)
        val frameBuffer = createFrameBuffer(outputTexture)

        try {
            drawTexture(program, inputTexture, frameBuffer, source.width, source.height) {
                setFilterUniforms(program, filter, source.width, source.height)
            }
            return readBitmap(source.width, source.height)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(frameBuffer), 0)
            GLES20.glDeleteTextures(2, intArrayOf(inputTexture, outputTexture), 0)
            GLES20.glDeleteProgram(program)
        }
    }

    private fun renderGradient(
        width: Int,
        height: Int,
        startColor: Int,
        endColor: Int,
        direction: FillGradientDirection
    ): Bitmap {
        val program = createProgram(VERTEX_SHADER, GRADIENT_FRAGMENT_SHADER)
        val outputTexture = createOutputTexture(width, height)
        val frameBuffer = createFrameBuffer(outputTexture)

        try {
            drawGradient(program, frameBuffer, width, height) {
                setColorUniform(program, "uStartColor", startColor)
                setColorUniform(program, "uEndColor", endColor)
                setGradientDirectionUniform(program, direction)
            }
            return readBitmap(width, height)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(frameBuffer), 0)
            GLES20.glDeleteTextures(1, intArrayOf(outputTexture), 0)
            GLES20.glDeleteProgram(program)
        }
    }

    private fun renderBlur(source: Bitmap, radius: Int): Bitmap {
        val clampedRadius = radius.coerceIn(0, BLUR_MAX_RADIUS)
        if (clampedRadius == 0) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val program = createProgram(VERTEX_SHADER, blurFragmentShader(clampedRadius))
        val inputTexture = createInputTexture(source)
        val intermediateTexture = createOutputTexture(source.width, source.height)
        val outputTexture = createOutputTexture(source.width, source.height)
        val intermediateFrameBuffer = createFrameBuffer(intermediateTexture)
        val outputFrameBuffer = createFrameBuffer(outputTexture)

        try {
            drawTexture(program, inputTexture, intermediateFrameBuffer, source.width, source.height) {
                setTexelOffset(program, 1f / source.width, 0f)
            }
            drawTexture(program, intermediateTexture, outputFrameBuffer, source.width, source.height) {
                setTexelOffset(program, 0f, 1f / source.height)
            }
            return readBitmap(source.width, source.height)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDeleteFramebuffers(2, intArrayOf(intermediateFrameBuffer, outputFrameBuffer), 0)
            GLES20.glDeleteTextures(3, intArrayOf(inputTexture, intermediateTexture, outputTexture), 0)
            GLES20.glDeleteProgram(program)
        }
    }

    private fun drawTexture(
        program: Int,
        inputTexture: Int,
        frameBuffer: Int,
        width: Int,
        height: Int,
        configureUniforms: () -> Unit
    ) {
        GLES20.glUseProgram(program)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        val textureCoordinateLocation = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
        val textureLocation = GLES20.glGetUniformLocation(program, "uTexture")
        check(positionLocation >= 0 && textureCoordinateLocation >= 0 && textureLocation >= 0) {
            "Missing shader attribute or uniform"
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture)
        GLES20.glUniform1i(textureLocation, 0)
        configureUniforms()

        VERTEX_BUFFER.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(
            positionLocation,
            COORDINATES_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            VERTEX_BUFFER
        )

        TEXTURE_COORDINATE_BUFFER.position(0)
        GLES20.glEnableVertexAttribArray(textureCoordinateLocation)
        GLES20.glVertexAttribPointer(
            textureCoordinateLocation,
            TEXTURE_COORDINATES_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            TEXTURE_COORDINATE_BUFFER
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        GLES20.glFinish()
        checkGlError("draw filter")

        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureCoordinateLocation)
    }

    private fun drawGradient(
        program: Int,
        frameBuffer: Int,
        width: Int,
        height: Int,
        configureUniforms: () -> Unit
    ) {
        GLES20.glUseProgram(program)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        val textureCoordinateLocation = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
        check(positionLocation >= 0 && textureCoordinateLocation >= 0) {
            "Missing shader attribute"
        }
        configureUniforms()

        VERTEX_BUFFER.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(
            positionLocation,
            COORDINATES_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            VERTEX_BUFFER
        )

        TEXTURE_COORDINATE_BUFFER.position(0)
        GLES20.glEnableVertexAttribArray(textureCoordinateLocation)
        GLES20.glVertexAttribPointer(
            textureCoordinateLocation,
            TEXTURE_COORDINATES_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            TEXTURE_COORDINATE_BUFFER
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        GLES20.glFinish()
        checkGlError("draw gradient")

        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureCoordinateLocation)
    }

    private fun checkTextureSize(width: Int, height: Int) {
        val maxTextureSize = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        check(width <= maxTextureSize[0] && height <= maxTextureSize[0]) {
            "Bitmap exceeds max GPU texture size"
        }
    }

    private fun createInputTexture(bitmap: Bitmap): Int {
        val texture = createTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        setTextureParameters()
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        checkGlError("upload input texture")
        return texture
    }

    private fun createOutputTexture(width: Int, height: Int): Int {
        val texture = createTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        setTextureParameters()
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        checkGlError("create output texture")
        return texture
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        check(textures[0] != 0) { "Could not create texture" }
        return textures[0]
    }

    private fun setTextureParameters() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun createFrameBuffer(texture: Int): Int {
        val frameBuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, frameBuffers, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            texture,
            0
        )
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            "Framebuffer is incomplete"
        }
        return frameBuffers[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        check(program != 0) { "Could not create GL program" }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val message = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("Could not link GL program: $message")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Could not create GL shader" }

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Could not compile GL shader: $message")
        }
        return shader
    }

    private fun readBitmap(width: Int, height: Int): Bitmap {
        val pixelCount = checkedPixelCount(width, height)
        val byteCount = checkedByteCount(pixelCount)
        val pixelBytes = ByteBuffer.allocateDirect(byteCount)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBytes)
        checkGlError("read pixels")

        val pixels = IntArray(pixelCount)
        pixelBytes.position(0)
        for (readY in 0 until height) {
            for (x in 0 until width) {
                val red = pixelBytes.get().toInt() and BYTE_MASK
                val green = pixelBytes.get().toInt() and BYTE_MASK
                val blue = pixelBytes.get().toInt() and BYTE_MASK
                val alpha = pixelBytes.get().toInt() and BYTE_MASK
                pixels[readY * width + x] = (alpha shl ALPHA_SHIFT) or
                    (red shl RED_SHIFT) or
                    (green shl GREEN_SHIFT) or
                    blue
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun checkedPixelCount(width: Int, height: Int): Int {
        val pixelCount = width.toLong() * height.toLong()
        require(pixelCount <= Int.MAX_VALUE) { "Bitmap is too large for GPU readback" }
        return pixelCount.toInt()
    }

    private fun checkedByteCount(pixelCount: Int): Int {
        val byteCount = pixelCount.toLong() * BYTES_PER_PIXEL
        require(byteCount <= Int.MAX_VALUE) { "Bitmap readback buffer is too large" }
        return byteCount.toInt()
    }

    private fun fragmentShader(filter: GpuFilter): String =
        when (filter) {
            GpuFilter.Invert -> INVERT_FRAGMENT_SHADER
            is GpuFilter.Brightness -> BRIGHTNESS_FRAGMENT_SHADER
            is GpuFilter.Contrast -> CONTRAST_FRAGMENT_SHADER
            is GpuFilter.Saturation -> SATURATION_FRAGMENT_SHADER
            is GpuFilter.Temperature -> TEMPERATURE_FRAGMENT_SHADER
            is GpuFilter.Highlights -> HIGHLIGHTS_FRAGMENT_SHADER
            is GpuFilter.Shadows -> SHADOWS_FRAGMENT_SHADER
            is GpuFilter.Sepia -> SEPIA_FRAGMENT_SHADER
            is GpuFilter.Sharpen -> SHARPEN_FRAGMENT_SHADER
            is GpuFilter.Blur -> blurFragmentShader(filter.radius.coerceIn(0, BLUR_MAX_RADIUS))
            is GpuFilter.Bloom -> BLOOM_FRAGMENT_SHADER
            is GpuFilter.Median -> MEDIAN_FRAGMENT_SHADER
            is GpuFilter.Bilateral -> BILATERAL_FRAGMENT_SHADER
            is GpuFilter.Vignette -> VIGNETTE_FRAGMENT_SHADER
            is GpuFilter.Clarity -> CLARITY_FRAGMENT_SHADER
            is GpuFilter.Exposure -> EXPOSURE_FRAGMENT_SHADER
            is GpuFilter.Vibrance -> VIBRANCE_FRAGMENT_SHADER
            is GpuFilter.Pixelate -> PIXELATE_FRAGMENT_SHADER
            is GpuFilter.Hue -> HUE_FRAGMENT_SHADER
        }

    private fun setFilterUniforms(program: Int, filter: GpuFilter, width: Int, height: Int) {
        when (filter) {
            GpuFilter.Invert -> Unit
            is GpuFilter.Brightness -> setUniform(program, "uBrightness", filter.offset)
            is GpuFilter.Contrast -> setUniform(program, "uContrast", filter.factor)
            is GpuFilter.Saturation -> setUniform(program, "uSaturation", filter.amount)
            is GpuFilter.Temperature -> setUniform(program, "uTemperature", filter.amount.coerceIn(-1f, 1f))
            is GpuFilter.Highlights -> setUniform(program, "uAmount", filter.amount.coerceIn(-1f, 1f))
            is GpuFilter.Shadows -> setUniform(program, "uAmount", filter.amount.coerceIn(-1f, 1f))
            is GpuFilter.Sepia -> setUniform(program, "uStrength", filter.strength)
            is GpuFilter.Sharpen -> {
                setUniform(program, "uSharpness", filter.strength.coerceIn(0f, 1f))
                setUniform(program, "uTexelSize", 1f / width, 1f / height)
            }
            is GpuFilter.Blur -> Unit
            is GpuFilter.Bloom -> {
                setUniform(program, "uStrength", filter.strength.coerceIn(0f, 1f))
                setUniform(program, "uTexelSize", 1f / width, 1f / height)
            }
            is GpuFilter.Median -> {
                setUniform(program, "uStrength", filter.strength.coerceIn(0, 100) / PERCENT_FLOAT)
                setUniform(program, "uTexelSize", 1f / width, 1f / height)
            }
            is GpuFilter.Bilateral -> {
                val strength = filter.strength.coerceIn(0, 100)
                setUniform(program, "uStrength", strength / PERCENT_FLOAT)
                setUniform(program, "uRangeFactor", bilateralRangeFactor(strength))
                setUniform(program, "uTexelSize", 1f / width, 1f / height)
            }
            is GpuFilter.Vignette -> setUniform(program, "uStrength", filter.strength.coerceIn(0f, 1f))
            is GpuFilter.Clarity -> {
                setUniform(program, "uStrength", filter.strength.coerceIn(0f, 1f))
                setUniform(program, "uTexelSize", 1f / width, 1f / height)
            }
            is GpuFilter.Exposure -> setUniform(program, "uExposure", filter.amount.coerceIn(-1f, 1f))
            is GpuFilter.Vibrance -> setUniform(program, "uVibrance", filter.amount.coerceIn(-1f, 1f))
            is GpuFilter.Pixelate -> {
                setUniform(program, "uImageSize", width.toFloat(), height.toFloat())
                setUniform(program, "uBlockSize", filter.blockSize.coerceIn(PIXELATE_MIN_BLOCK_SIZE, PIXELATE_MAX_BLOCK_SIZE).toFloat())
            }
            is GpuFilter.Hue -> setUniform(program, "uHueShift", filter.shift.coerceIn(-0.5f, 0.5f))
        }
    }

    private fun setUniform(program: Int, name: String, value: Float) {
        val location = GLES20.glGetUniformLocation(program, name)
        check(location >= 0) { "Missing shader uniform: $name" }
        GLES20.glUniform1f(location, value)
    }

    private fun setUniform(program: Int, name: String, x: Float, y: Float) {
        val location = GLES20.glGetUniformLocation(program, name)
        check(location >= 0) { "Missing shader uniform: $name" }
        GLES20.glUniform2f(location, x, y)
    }

    private fun setColorUniform(program: Int, name: String, color: Int) {
        val location = GLES20.glGetUniformLocation(program, name)
        check(location >= 0) { "Missing shader uniform: $name" }
        GLES20.glUniform4f(
            location,
            ((color ushr RED_SHIFT) and BYTE_MASK) / COLOR_MAX_FLOAT,
            ((color ushr GREEN_SHIFT) and BYTE_MASK) / COLOR_MAX_FLOAT,
            (color and BYTE_MASK) / COLOR_MAX_FLOAT,
            ((color ushr ALPHA_SHIFT) and BYTE_MASK) / COLOR_MAX_FLOAT
        )
    }

    private fun setGradientDirectionUniform(program: Int, direction: FillGradientDirection) {
        val location = GLES20.glGetUniformLocation(program, "uDirection")
        check(location >= 0) { "Missing shader uniform: uDirection" }
        GLES20.glUniform1i(
            location,
            when (direction) {
                FillGradientDirection.TOP_BOTTOM -> GRADIENT_DIRECTION_TOP_BOTTOM
                FillGradientDirection.LEFT_RIGHT -> GRADIENT_DIRECTION_LEFT_RIGHT
                FillGradientDirection.LEFT_TOP_RIGHT_BOTTOM -> GRADIENT_DIRECTION_LEFT_TOP
                FillGradientDirection.RIGHT_TOP_LEFT_BOTTOM -> GRADIENT_DIRECTION_RIGHT_TOP
                FillGradientDirection.BOTTOM_TOP -> GRADIENT_DIRECTION_BOTTOM_TOP
                FillGradientDirection.RIGHT_LEFT -> GRADIENT_DIRECTION_RIGHT_LEFT
                FillGradientDirection.LEFT_BOTTOM_RIGHT_TOP -> GRADIENT_DIRECTION_LEFT_BOTTOM
                FillGradientDirection.RIGHT_BOTTOM_LEFT_TOP -> GRADIENT_DIRECTION_RIGHT_BOTTOM
                FillGradientDirection.RADIAL -> GRADIENT_DIRECTION_RADIAL
                FillGradientDirection.RADIAL_OUTSIDE_IN -> GRADIENT_DIRECTION_RADIAL_OUTSIDE_IN
                FillGradientDirection.RANDOM_WAVE,
                FillGradientDirection.WAVE_LEFT_TOP_RIGHT_BOTTOM,
                FillGradientDirection.WAVE_LEFT_RIGHT,
                FillGradientDirection.WAVE_LEFT_BOTTOM_RIGHT_TOP,
                FillGradientDirection.WAVE_BOTTOM_TOP,
                FillGradientDirection.WAVE_RIGHT_BOTTOM_LEFT_TOP,
                FillGradientDirection.WAVE_RIGHT_LEFT,
                FillGradientDirection.WAVE_RIGHT_TOP_LEFT_BOTTOM -> GRADIENT_DIRECTION_TOP_BOTTOM
            }
        )
    }

    private fun setTexelOffset(program: Int, x: Float, y: Float) {
        val location = GLES20.glGetUniformLocation(program, "uTexelOffset")
        check(location >= 0) { "Missing shader uniform: uTexelOffset" }
        GLES20.glUniform2f(location, x, y)
    }

    private fun checkGlError(operation: String) {
        val error = GLES20.glGetError()
        check(error == GLES20.GL_NO_ERROR) { "GL error after $operation: $error" }
    }

    private class EglSession(width: Int, height: Int) {
        private val display: EGLDisplay
        private val context: EGLContext
        private val surface: EGLSurface

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "Could not get EGL display" }

            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Could not initialize EGL" }
            check(EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)) { eglError("Could not bind OpenGL ES API") }

            val config = chooseConfig(display)
            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, OPENGL_ES_VERSION, EGL14.EGL_NONE),
                0
            )
            check(context != EGL14.EGL_NO_CONTEXT) { eglError("Could not create EGL context") }

            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE),
                0
            )
            check(surface != EGL14.EGL_NO_SURFACE) { eglError("Could not create EGL surface") }
        }

        fun makeCurrent() {
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                eglError("Could not make EGL current")
            }
        }

        fun release() {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }

        private fun chooseConfig(display: EGLDisplay): EGLConfig {
            val configs = arrayOfNulls<EGLConfig>(1)
            val numberOfConfigs = IntArray(1)
            val attributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,
                COLOR_BITS,
                EGL14.EGL_GREEN_SIZE,
                COLOR_BITS,
                EGL14.EGL_BLUE_SIZE,
                COLOR_BITS,
                EGL14.EGL_ALPHA_SIZE,
                COLOR_BITS,
                EGL14.EGL_NONE
            )
            check(
                EGL14.eglChooseConfig(
                    display,
                    attributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    numberOfConfigs,
                    0
                ) && numberOfConfigs[0] > 0
            ) {
                "Could not choose EGL config"
            }
            return configs[0] ?: error("EGL config was null")
        }

        private fun eglError(message: String): String =
            "$message: ${EGL14.eglGetError()}"
    }

    private const val OPENGL_ES_VERSION = 2
    private const val EGL_OPENGL_ES2_BIT = 4
    private const val COLOR_BITS = 8
    private const val BYTES_PER_PIXEL = 4
    private const val BYTE_MASK = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val COORDINATES_PER_VERTEX = 2
    private const val TEXTURE_COORDINATES_PER_VERTEX = 2
    private const val VERTEX_COUNT = 4
    private const val BLUR_MAX_RADIUS = 25
    private const val BLUR_SCALED_MAX_RADIUS = 12
    private const val BLUR_FULL_RES_MAX_SIDE = 2_048
    private const val COLOR_MAX_FLOAT = 255f
    private const val PERCENT_FLOAT = 100f
    private const val BILATERAL_MIN_RANGE_FACTOR = 4f
    private const val BILATERAL_MAX_RANGE_FACTOR = 150f
    private const val PIXELATE_MIN_BLOCK_SIZE = 2
    private const val PIXELATE_MAX_BLOCK_SIZE = 50
    private const val GRADIENT_DIRECTION_TOP_BOTTOM = 0
    private const val GRADIENT_DIRECTION_LEFT_RIGHT = 1
    private const val GRADIENT_DIRECTION_LEFT_TOP = 2
    private const val GRADIENT_DIRECTION_RIGHT_TOP = 3
    private const val GRADIENT_DIRECTION_RADIAL = 4
    private const val GRADIENT_DIRECTION_BOTTOM_TOP = 5
    private const val GRADIENT_DIRECTION_RIGHT_LEFT = 6
    private const val GRADIENT_DIRECTION_LEFT_BOTTOM = 7
    private const val GRADIENT_DIRECTION_RIGHT_BOTTOM = 8
    private const val GRADIENT_DIRECTION_RADIAL_OUTSIDE_IN = 9

    private val VERTEX_BUFFER: FloatBuffer = floatBufferOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    private val TEXTURE_COORDINATE_BUFFER: FloatBuffer = floatBufferOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )

    private fun floatBufferOf(vararg values: Float): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTextureCoordinate;
        varying vec2 vTextureCoordinate;

        void main() {
            gl_Position = aPosition;
            vTextureCoordinate = aTextureCoordinate;
        }
    """

    private const val INVERT_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            gl_FragColor = vec4(1.0 - color.rgb, color.a);
        }
    """

    private const val BRIGHTNESS_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uBrightness;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            gl_FragColor = vec4(clamp(color.rgb + vec3(uBrightness), 0.0, 1.0), color.a);
        }
    """

    private const val CONTRAST_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uContrast;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            vec3 adjusted = (color.rgb - vec3(0.5)) * uContrast + vec3(0.5);
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), color.a);
        }
    """

    private const val SATURATION_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uSaturation;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            vec3 adjusted = mix(vec3(gray), color.rgb, uSaturation);
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), color.a);
        }
    """

    private const val TEMPERATURE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uTemperature;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            vec3 adjustment = vec3(0.16, 0.04, -0.16) * uTemperature;
            gl_FragColor = vec4(clamp(color.rgb + adjustment, 0.0, 1.0), color.a);
        }
    """

    private const val HIGHLIGHTS_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uAmount;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            float mask = smoothstep(0.48, 0.92, luminance);
            vec3 adjusted = color.rgb + vec3(uAmount * 0.28 * mask);
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), color.a);
        }
    """

    private const val SHADOWS_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uAmount;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            float mask = 1.0 - smoothstep(0.08, 0.55, luminance);
            vec3 adjusted = color.rgb + vec3(uAmount * 0.30 * mask);
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), color.a);
        }
    """

    private const val SEPIA_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uStrength;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            vec3 sepia = vec3(
                dot(color.rgb, vec3(0.393, 0.769, 0.189)),
                dot(color.rgb, vec3(0.349, 0.686, 0.168)),
                dot(color.rgb, vec3(0.272, 0.534, 0.131))
            );
            vec3 adjusted = mix(color.rgb, clamp(sepia, 0.0, 1.0), uStrength);
            gl_FragColor = vec4(adjusted, color.a);
        }
    """

    private const val SHARPEN_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uSharpness;
        uniform vec2 uTexelSize;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 center = texture2D(uTexture, vTextureCoordinate);
            vec3 left = texture2D(uTexture, vTextureCoordinate - vec2(uTexelSize.x, 0.0)).rgb;
            vec3 right = texture2D(uTexture, vTextureCoordinate + vec2(uTexelSize.x, 0.0)).rgb;
            vec3 up = texture2D(uTexture, vTextureCoordinate - vec2(0.0, uTexelSize.y)).rgb;
            vec3 down = texture2D(uTexture, vTextureCoordinate + vec2(0.0, uTexelSize.y)).rgb;
            vec3 adjusted = center.rgb * (1.0 + 4.0 * uSharpness) - (left + right + up + down) * uSharpness;
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), center.a);
        }
    """

    private const val VIGNETTE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uStrength;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            vec2 centered = vTextureCoordinate - vec2(0.5);
            float distanceFromCenter = length(centered);
            float edge = smoothstep(0.32, 0.78, distanceFromCenter);
            float vignette = 1.0 - edge * uStrength;
            gl_FragColor = vec4(clamp(color.rgb * vignette, 0.0, 1.0), color.a);
        }
    """

    private const val CLARITY_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uStrength;
        uniform vec2 uTexelSize;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 center = texture2D(uTexture, vTextureCoordinate);
            vec2 radius = uTexelSize * 2.0;
            vec3 average = (
                texture2D(uTexture, vTextureCoordinate + vec2(-radius.x, 0.0)).rgb +
                texture2D(uTexture, vTextureCoordinate + vec2(radius.x, 0.0)).rgb +
                texture2D(uTexture, vTextureCoordinate + vec2(0.0, -radius.y)).rgb +
                texture2D(uTexture, vTextureCoordinate + vec2(0.0, radius.y)).rgb +
                texture2D(uTexture, vTextureCoordinate + vec2(-radius.x, -radius.y)).rgb +
                texture2D(uTexture, vTextureCoordinate + vec2(radius.x, -radius.y)).rgb +
                texture2D(uTexture, vTextureCoordinate + vec2(-radius.x, radius.y)).rgb +
                texture2D(uTexture, vTextureCoordinate + vec2(radius.x, radius.y)).rgb
            ) * 0.125;
            vec3 detail = center.rgb - average;
            float luminance = dot(center.rgb, vec3(0.299, 0.587, 0.114));
            float midtoneMask = smoothstep(0.06, 0.34, luminance) * (1.0 - smoothstep(0.72, 0.96, luminance));
            vec3 adjusted = center.rgb + detail * uStrength * 1.35 * midtoneMask;
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), center.a);
        }
    """

    private const val EXPOSURE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uExposure;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            float factor = pow(2.0, uExposure);
            vec3 adjusted = color.rgb * factor;
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), color.a);
        }
    """

    private const val VIBRANCE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uVibrance;
        varying vec2 vTextureCoordinate;

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            float maxChannel = max(color.r, max(color.g, color.b));
            float minChannel = min(color.r, min(color.g, color.b));
            float saturation = maxChannel - minChannel;
            float amount = 1.0 + uVibrance * (1.0 - saturation);
            vec3 adjusted = mix(vec3(gray), color.rgb, amount);
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), color.a);
        }
    """

    private const val PIXELATE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform vec2 uImageSize;
        uniform float uBlockSize;
        varying vec2 vTextureCoordinate;

        void main() {
            vec2 pixel = vTextureCoordinate * uImageSize;
            vec2 blockStart = floor(pixel / uBlockSize) * uBlockSize;
            vec2 blockEnd = min(blockStart + vec2(uBlockSize), uImageSize);
            vec2 sampleSize = blockEnd - blockStart;
            vec4 sum = vec4(0.0);

            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    vec2 sampleStep = (vec2(float(x), float(y)) + vec2(0.5)) / 5.0;
                    vec2 samplePixel = blockStart + sampleSize * sampleStep;
                    vec2 clampedPixel = clamp(samplePixel, vec2(0.5), uImageSize - vec2(0.5));
                    sum += texture2D(uTexture, clampedPixel / uImageSize);
                }
            }

            gl_FragColor = sum / 25.0;
        }
    """

    private const val HUE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uHueShift;
        varying vec2 vTextureCoordinate;

        vec3 rgbToHsv(vec3 c) {
            vec4 k = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, k.wz), vec4(c.gb, k.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 0.00001;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        vec3 hsvToRgb(vec3 c) {
            vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
            return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
        }

        void main() {
            vec4 color = texture2D(uTexture, vTextureCoordinate);
            vec3 hsv = rgbToHsv(color.rgb);
            hsv.x = fract(hsv.x + uHueShift + 1.0);
            gl_FragColor = vec4(clamp(hsvToRgb(hsv), 0.0, 1.0), color.a);
        }
    """

    private const val GRADIENT_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec4 uStartColor;
        uniform vec4 uEndColor;
        uniform int uDirection;
        varying vec2 vTextureCoordinate;

        void main() {
            float fraction = 0.0;
            if (uDirection == 0) {
                fraction = vTextureCoordinate.y;
            } else if (uDirection == 1) {
                fraction = vTextureCoordinate.x;
            } else if (uDirection == 2) {
                fraction = (vTextureCoordinate.x + vTextureCoordinate.y) * 0.5;
            } else if (uDirection == 3) {
                fraction = ((1.0 - vTextureCoordinate.x) + vTextureCoordinate.y) * 0.5;
            } else if (uDirection == 5) {
                fraction = 1.0 - vTextureCoordinate.y;
            } else if (uDirection == 6) {
                fraction = 1.0 - vTextureCoordinate.x;
            } else if (uDirection == 7) {
                fraction = (vTextureCoordinate.x + (1.0 - vTextureCoordinate.y)) * 0.5;
            } else if (uDirection == 8) {
                fraction = ((1.0 - vTextureCoordinate.x) + (1.0 - vTextureCoordinate.y)) * 0.5;
            } else if (uDirection == 9) {
                fraction = 1.0 - length(vTextureCoordinate - vec2(0.5, 0.5)) / length(vec2(0.5, 0.5));
            } else {
                fraction = length(vTextureCoordinate - vec2(0.5, 0.5)) / length(vec2(0.5, 0.5));
            }
            gl_FragColor = mix(uStartColor, uEndColor, clamp(fraction, 0.0, 1.0));
        }
    """

    private fun blurFragmentShader(radius: Int): String {
        val samples = StringBuilder()
        for (offset in -radius..radius) {
            samples.append(
                "sum += texture2D(uTexture, vTextureCoordinate + uTexelOffset * $offset.0);\n"
            )
        }
        val sampleCount = radius * 2 + 1
        return """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTexelOffset;
            varying vec2 vTextureCoordinate;

            void main() {
                vec4 sum = vec4(0.0);
                $samples
                gl_FragColor = sum / $sampleCount.0;
            }
        """
    }

    private const val BLOOM_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize;
        uniform float uStrength;
        varying vec2 vTextureCoordinate;

        vec3 brightColor(vec2 coordinate) {
            vec3 color = texture2D(uTexture, coordinate).rgb;
            float luminance = dot(color, vec3(0.299, 0.587, 0.114));
            return color * smoothstep(0.62, 0.95, luminance);
        }

        void main() {
            vec4 base = texture2D(uTexture, vTextureCoordinate);
            vec2 radius = uTexelSize * (2.0 + uStrength * 8.0);
            vec3 glow = brightColor(vTextureCoordinate) * 0.20;
            glow += brightColor(vTextureCoordinate + vec2(radius.x, 0.0)) * 0.12;
            glow += brightColor(vTextureCoordinate - vec2(radius.x, 0.0)) * 0.12;
            glow += brightColor(vTextureCoordinate + vec2(0.0, radius.y)) * 0.12;
            glow += brightColor(vTextureCoordinate - vec2(0.0, radius.y)) * 0.12;
            glow += brightColor(vTextureCoordinate + radius) * 0.08;
            glow += brightColor(vTextureCoordinate - radius) * 0.08;
            glow += brightColor(vTextureCoordinate + vec2(radius.x, -radius.y)) * 0.08;
            glow += brightColor(vTextureCoordinate + vec2(-radius.x, radius.y)) * 0.08;
            vec3 adjusted = base.rgb + glow * (uStrength * 1.6);
            gl_FragColor = vec4(clamp(adjusted, 0.0, 1.0), base.a);
        }
    """

    private const val MEDIAN_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize;
        uniform float uStrength;
        varying vec2 vTextureCoordinate;

        void sort2(inout vec3 first, inout vec3 second) {
            vec3 lower = min(first, second);
            second = max(first, second);
            first = lower;
        }

        void main() {
            vec4 center = texture2D(uTexture, vTextureCoordinate);
            vec3 c0 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(-1.0, -1.0)).rgb;
            vec3 c1 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(0.0, -1.0)).rgb;
            vec3 c2 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(1.0, -1.0)).rgb;
            vec3 c3 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(-1.0, 0.0)).rgb;
            vec3 c4 = center.rgb;
            vec3 c5 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(1.0, 0.0)).rgb;
            vec3 c6 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(-1.0, 1.0)).rgb;
            vec3 c7 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(0.0, 1.0)).rgb;
            vec3 c8 = texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(1.0, 1.0)).rgb;

            sort2(c0, c1); sort2(c1, c2); sort2(c0, c1);
            sort2(c2, c3); sort2(c1, c2); sort2(c0, c1);
            sort2(c3, c4); sort2(c2, c3); sort2(c1, c2); sort2(c0, c1);
            sort2(c4, c5); sort2(c3, c4); sort2(c2, c3); sort2(c1, c2); sort2(c0, c1);
            sort2(c5, c6); sort2(c4, c5); sort2(c3, c4); sort2(c2, c3); sort2(c1, c2); sort2(c0, c1);
            sort2(c6, c7); sort2(c5, c6); sort2(c4, c5); sort2(c3, c4); sort2(c2, c3); sort2(c1, c2); sort2(c0, c1);
            sort2(c7, c8); sort2(c6, c7); sort2(c5, c6); sort2(c4, c5); sort2(c3, c4); sort2(c2, c3); sort2(c1, c2); sort2(c0, c1);

            gl_FragColor = vec4(mix(center.rgb, c4, uStrength), center.a);
        }
    """

    private const val BILATERAL_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize;
        uniform float uStrength;
        uniform float uRangeFactor;
        varying vec2 vTextureCoordinate;

        void accumulate(inout vec3 sum, inout float totalWeight, vec3 center, vec3 sample, float spatialWeight) {
            vec3 difference = sample - center;
            float rangeWeight = exp(-dot(difference, difference) * uRangeFactor);
            float weight = spatialWeight * rangeWeight;
            sum += sample * weight;
            totalWeight += weight;
        }

        void main() {
            vec4 center = texture2D(uTexture, vTextureCoordinate);
            vec3 sum = vec3(0.0);
            float totalWeight = 0.0;
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(-1.0, -1.0)).rgb, 0.7071);
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(0.0, -1.0)).rgb, 1.0);
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(1.0, -1.0)).rgb, 0.7071);
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(-1.0, 0.0)).rgb, 1.0);
            accumulate(sum, totalWeight, center.rgb, center.rgb, 1.5);
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(1.0, 0.0)).rgb, 1.0);
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(-1.0, 1.0)).rgb, 0.7071);
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(0.0, 1.0)).rgb, 1.0);
            accumulate(sum, totalWeight, center.rgb, texture2D(uTexture, vTextureCoordinate + uTexelSize * vec2(1.0, 1.0)).rgb, 0.7071);
            vec3 smoothed = sum / max(totalWeight, 0.0001);
            gl_FragColor = vec4(mix(center.rgb, smoothed, uStrength), center.a);
        }
    """

    private fun bilateralRangeFactor(strength: Int): Float {
        val progress = strength.coerceIn(0, 100) / PERCENT_FLOAT
        return BILATERAL_MAX_RANGE_FACTOR +
            (BILATERAL_MIN_RANGE_FACTOR - BILATERAL_MAX_RANGE_FACTOR) * progress
    }
}
