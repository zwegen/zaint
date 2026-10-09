package de.zwegen.zpaint.test.espresso.util.wrappers

import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import de.zwegen.zpaint.R
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.test.espresso.util.MainActivityHelper
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher

class DrawingSurfaceInteraction private constructor() :
    CustomViewInteraction(Espresso.onView(withId(R.id.zpaint_drawing_surface_view))) {
    fun checkPixelColor(
        @ColorInt expectedColor: Int,
        coordinateProvider: CoordinatesProvider
    ): DrawingSurfaceInteraction {
        check(ViewAssertions.matches(object : TypeSafeMatcher<View?>() {
            override fun describeTo(description: Description) {
                description.appendText(
                    "Color at coordinates is " + Integer.toHexString(
                        expectedColor
                    )
                )
            }

            override fun matchesSafely(view: View?): Boolean {
                val activity = MainActivityHelper.getMainActivityFromView(view!!)
                val currentBitmap = activity.layerModel.getBitmapOfAllLayers()
                val coordinates = coordinateProvider.calculateCoordinates(view)
                val actualColor =
                    currentBitmap!!.getPixel(coordinates[0].toInt(), coordinates[1].toInt())
                return expectedColor == actualColor
            }
        }))
        return this
    }

    fun checkPixelColorOnLayer(
        @ColorInt expectedColor: Int,
        coordinateProvider: CoordinatesProvider
    ): DrawingSurfaceInteraction {
        check(ViewAssertions.matches(object : TypeSafeMatcher<View?>() {
            override fun describeTo(description: Description) {
                description.appendText(
                    "Color at coordinates is " + Integer.toHexString(
                        expectedColor
                    )
                )
            }

            override fun matchesSafely(view: View?): Boolean {
                val activity = MainActivityHelper.getMainActivityFromView(view!!)
                val currentBitmap = activity.layerModel.currentLayer!!.bitmap
                val coordinates = coordinateProvider.calculateCoordinates(view)
                val actualColor =
                    currentBitmap.getPixel(coordinates[0].toInt(), coordinates[1].toInt())
                return expectedColor == actualColor
            }
        }))
        return this
    }

    fun checkPixelColor(
        @ColorInt expectedColor: Int,
        x: Float,
        y: Float
    ): DrawingSurfaceInteraction {
        check(ViewAssertions.matches(object : TypeSafeMatcher<View?>() {
            override fun describeTo(description: Description) {
                description.appendText(
                    "Color at coordinates is " + Integer.toHexString(
                        expectedColor
                    )
                )
            }

            override fun matchesSafely(view: View?): Boolean {
                val activity = MainActivityHelper.getMainActivityFromView(view!!)
                val currentBitmap = activity.layerModel.getBitmapOfAllLayers()
                val actualColor = currentBitmap!!.getPixel(x.toInt(), y.toInt())
                return expectedColor == actualColor
            }
        }))
        return this
    }

    fun checkPixelColorResource(
        @ColorRes expectedColorRes: Int,
        coordinateProvider: CoordinatesProvider
    ): DrawingSurfaceInteraction {
        val expectedColor = ContextCompat.getColor(
            InstrumentationRegistry.getInstrumentation().targetContext,
            expectedColorRes
        )
        return checkPixelColor(expectedColor, coordinateProvider)
    }

    fun checkBitmapDimension(expectedWidth: Int, expectedHeight: Int): DrawingSurfaceInteraction {
        check(ViewAssertions.matches(object : TypeSafeMatcher<View?>() {
            override fun describeTo(description: Description) {
                description.appendText(
                    "Bitmap has is size " +
                          expectedWidth + "x and " +
                          expectedHeight + "y"
                )
            }

            override fun matchesSafely(view: View?): Boolean {
                val activity = MainActivityHelper.getMainActivityFromView(view!!)
                val layerModel = activity.layerModel
                val bitmap = layerModel.currentLayer!!.bitmap
                return expectedWidth == bitmap.width && expectedHeight == bitmap.height
            }
        }))
        return this
    }

    fun checkLayerDimensions(expectedWidth: Int, expectedHeight: Int): DrawingSurfaceInteraction {
        checkThatLayerDimensions(Matchers.`is`(expectedWidth), Matchers.`is`(expectedHeight))
        return this
    }

    fun checkThatLayerDimensions(
        matchesWidth: Matcher<Int>,
        matchesHeight: Matcher<Int>
    ): DrawingSurfaceInteraction {
        check(ViewAssertions.matches(object : TypeSafeMatcher<View?>() {
            override fun describeTo(description: Description) {
                description.appendText("All layers have expected size")
            }

            override fun matchesSafely(view: View?): Boolean {
                val activity = MainActivityHelper.getMainActivityFromView(view!!)
                val layerModel = activity.layerModel
                for (layer: ZaintLayerContracts.ZaintLayer in layerModel.layers) {
                    val bitmap = layer.bitmap
                    if (!matchesWidth.matches(bitmap.width) || !matchesHeight.matches(bitmap.height)) {
                        return false
                    }
                }
                return true
            }
        }))
        return this
    }

    companion object {
        fun onDrawingSurfaceView(): DrawingSurfaceInteraction = DrawingSurfaceInteraction()
    }
}
