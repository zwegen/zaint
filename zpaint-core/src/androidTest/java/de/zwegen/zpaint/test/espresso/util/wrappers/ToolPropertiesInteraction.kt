package de.zwegen.zpaint.test.espresso.util.wrappers

import android.graphics.Paint.Cap
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import de.zwegen.zpaint.test.espresso.util.EspressoUtils.mainActivity
import de.zwegen.zpaint.tools.Tool
import org.junit.Assert

class ToolPropertiesInteraction private constructor() : CustomViewInteraction(null) {
    fun checkMatchesColor(@ColorInt expectedColor: Int): ToolPropertiesInteraction {
        Assert.assertEquals(expectedColor.toLong(), currentTool.drawPaint.color.toLong())
        return this
    }

    fun checkDoesNotMatchColor(@ColorInt color: Int): ToolPropertiesInteraction {
        Assert.assertNotEquals(color.toLong(), currentTool.drawPaint.color.toLong())
        return this
    }

    fun checkMatchesColorResource(@ColorRes expectedColorRes: Int): ToolPropertiesInteraction {
        val expectedColor = ContextCompat.getColor(
                InstrumentationRegistry.getInstrumentation().targetContext,
                expectedColorRes
        )
        return checkMatchesColor(expectedColor)
    }

    fun checkCap(expectedCap: Cap?): ToolPropertiesInteraction {
        val strokePaint = currentTool.drawPaint
        Assert.assertEquals(expectedCap, strokePaint.strokeCap)
        return this
    }

    fun setCap(expectedCap: Cap?): ToolPropertiesInteraction {
        currentTool.changePaintStrokeCap(expectedCap!!)
        return this
    }

    fun checkStrokeWidth(expectedStrokeWidth: Float): ToolPropertiesInteraction {
        val strokePaint = currentTool.drawPaint
        Assert.assertEquals(expectedStrokeWidth, strokePaint.strokeWidth, Float.MIN_VALUE)
        return this
    }

    fun setStrokeWidth(expectedStrokeWidth: Float): ToolPropertiesInteraction {
        currentTool.changePaintStrokeWidth(expectedStrokeWidth.toInt())
        return this
    }

    fun setColor(color: Int): ToolPropertiesInteraction {
        currentTool.changePaintColor(color)
        return this
    }

    private val currentTool: Tool
        get() = mainActivity.toolReference.tool as Tool

    fun setColorResource(@ColorRes colorResource: Int): ToolPropertiesInteraction {
        val color = ContextCompat.getColor(
                InstrumentationRegistry.getInstrumentation().targetContext,
                colorResource
        )
        return setColor(color)
    }

    companion object {
        @JvmStatic
        fun onToolProperties(): ToolPropertiesInteraction = ToolPropertiesInteraction()
    }
}
