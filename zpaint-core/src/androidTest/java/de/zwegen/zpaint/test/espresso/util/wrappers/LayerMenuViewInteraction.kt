package de.zwegen.zpaint.test.espresso.util.wrappers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import de.zwegen.zpaint.R
import de.zwegen.zpaint.test.espresso.util.UiInteractions
import de.zwegen.zpaint.test.espresso.util.wrappers.BottomNavigationViewInteraction.Companion.onBottomNavigationView
import de.zwegen.zpaint.test.utils.RecyclerViewMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

class LayerMenuViewInteraction private constructor() : CustomViewInteraction(Espresso.onView(withId(R.id.zpaint_nav_view_layer))) {
    fun onButtonAdd(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_layer_side_nav_button_add))

    fun onButtonDelete(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_layer_side_nav_button_delete))

    fun onLayerList(): ViewInteraction = Espresso.onView(withId(R.id.zpaint_layer_side_nav_list))

    fun checkLayerCount(count: Int): LayerMenuViewInteraction {
        onLayerList()
                .check(UiInteractions.assertRecyclerViewCount(count))
        return this
    }

    fun performOpen(): LayerMenuViewInteraction {
        onBottomNavigationView()
                .onLayersClicked()
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        return this
    }

    fun performSetOpacityTo(opacityPercentage: Int, listPosition: Int): LayerMenuViewInteraction {
        Espresso.onView(withRecyclerView(R.id.zpaint_layer_side_nav_list)
                .atPositionOnView(listPosition, R.id.zpaint_layer_opacity_seekbar))
                .perform(UiInteractions.setProgress(opacityPercentage))
        return this
    }

    fun performClose(): LayerMenuViewInteraction {
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withId(R.id.zpaint_drawer_layout))
                .perform(DrawerActions.close(Gravity.END))
        return this
    }

    fun performSelectLayer(listPosition: Int): LayerMenuViewInteraction {
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withRecyclerView(R.id.zpaint_layer_side_nav_list)
                .atPositionOnView(listPosition, R.id.zpaint_layer_preview_container))
                .perform(ViewActions.click())
        return this
    }

    fun performScrollToPositionInLayerNavigation(listPosition: Int): LayerMenuViewInteraction {
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withId(R.id.zpaint_layer_side_nav_list))
                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(listPosition))
        return this
    }

    fun performStartDragging(listPosition: Int): LayerMenuViewInteraction {
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withIndex(withId(R.id.zpaint_layer_drag_handle), listPosition)).perform(ViewActions.click())
        return this
    }

    fun performAddLayer(): LayerMenuViewInteraction {
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        onButtonAdd()
                .perform(ViewActions.click())
        return this
    }

    fun performDeleteLayer(): LayerMenuViewInteraction {
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        onButtonDelete()
                .perform(ViewActions.click())
        return this
    }

    fun performToggleLayerVisibility(position: Int): LayerMenuViewInteraction {
        check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withIndex(withId(R.id.zpaint_checkbox_layer), position)).perform(ViewActions.click())
        return this
    }

    fun checkLayerAtPositionHasTopLeftPixelWithColor(listPosition: Int, @ColorInt expectedColor: Int): LayerMenuViewInteraction {
        Espresso.onView(withIndex(withId(R.id.zpaint_item_layer_image), listPosition))
                .check(ViewAssertions.matches(object : TypeSafeMatcher<View>() {
                    override fun describeTo(description: Description) {
                        description.appendText("Color at coordinates is " + Integer.toHexString(expectedColor))
                    }

                    override fun matchesSafely(view: View): Boolean {
                        val bitmap = getBitmap((view as ImageView).drawable)
                        val actualColor = bitmap.getPixel(0, 0)
                        return actualColor == expectedColor
                    }
                }))
        return this
    }

    private fun getBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        fun onLayerMenuView(): LayerMenuViewInteraction = LayerMenuViewInteraction()

        fun withRecyclerView(recyclerViewId: Int): RecyclerViewMatcher = RecyclerViewMatcher(recyclerViewId)

        fun withIndex(matcher: Matcher<View?>, index: Int): TypeSafeMatcher<View?> {
            return object : TypeSafeMatcher<View?>() {
                var currentIndex = 0
                override fun describeTo(description: Description) {
                    description.appendText("with index: ")
                    description.appendValue(index)
                    matcher.describeTo(description)
                }

                public override fun matchesSafely(view: View?): Boolean = matcher.matches(view) && currentIndex++ == index
            }
        }
    }
}
