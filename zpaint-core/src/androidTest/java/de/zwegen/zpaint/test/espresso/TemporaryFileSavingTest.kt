package de.zwegen.zpaint.test.espresso

import android.content.Intent
import android.graphics.Color
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.R
import de.zwegen.zpaint.common.TEMP_IMAGE_PATH
import de.zwegen.zpaint.test.espresso.util.BitmapLocationProvider
import de.zwegen.zpaint.test.espresso.util.DrawingSurfaceLocationProvider
import de.zwegen.zpaint.test.espresso.util.UiInteractions
import de.zwegen.zpaint.test.espresso.util.wrappers.DrawingSurfaceInteraction.Companion.onDrawingSurfaceView
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction
import de.zwegen.zpaint.test.espresso.util.wrappers.TopBarViewInteraction
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.ui.Perspective
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val THREAD_WAITING_TIME: Long = 2500
private const val THREAD_SHORT_WAITING_TIME: Long = 1000

@RunWith(AndroidJUnit4::class)
class TemporaryFileSavingTest {

    @get:Rule
    val launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)

    private lateinit var workspace: Workspace
    private lateinit var perspective: Perspective
    private lateinit var toolReference: ToolReference
    private lateinit var mainActivity: ZaintEditorActivity
    private lateinit var intent: Intent

    @Before
    fun setUp() {
        ToolBarViewInteraction.onToolBarView()
            .performSelectTool(ZaintToolKind.BRUSH)
        intent = Intent().putExtra("isTemporaryFileSavingTest", true)
        mainActivity = launchActivityRule.launchActivity(intent)
        workspace = mainActivity.workspace
        perspective = mainActivity.perspective
        toolReference = mainActivity.toolReference
        val file = File(mainActivity.filesDir, TEMP_IMAGE_PATH)
        if (file.exists()) {
            file.delete()
        }
    }

    @Test
    fun testOneUserInteraction() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        Thread.sleep(THREAD_WAITING_TIME)
        launchActivityRule.finishActivity()
        launchActivityRule.launchActivity(intent)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
    }

    @Test
    fun testNoWaitingTime() {
        onDrawingSurfaceView()
                .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        launchActivityRule.finishActivity()
        launchActivityRule.launchActivity(intent)
        onDrawingSurfaceView()
                .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
    }

    @Test
    fun testTooShortWaitingTime() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        Thread.sleep(THREAD_SHORT_WAITING_TIME)
        launchActivityRule.finishActivity()
        launchActivityRule.launchActivity(intent)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
    }

    @Test
    fun testMultipleUserInteractions() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.HALFWAY_TOP_LEFT))
        Thread.sleep(THREAD_WAITING_TIME)
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.HALFWAY_BOTTOM_RIGHT))
        Thread.sleep(THREAD_WAITING_TIME)
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.HALFWAY_BOTTOM_LEFT))
        launchActivityRule.finishActivity()
        launchActivityRule.launchActivity(intent)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.MIDDLE)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_TOP_LEFT)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_BOTTOM_RIGHT)
        onDrawingSurfaceView()
            .checkPixelColor(Color.BLACK, BitmapLocationProvider.HALFWAY_BOTTOM_LEFT)
    }

    @Test
    fun testTempFilesDeletedAfterCreatingNewImage() {
        onDrawingSurfaceView()
            .perform(UiInteractions.touchAt(DrawingSurfaceLocationProvider.MIDDLE))
        Thread.sleep(THREAD_WAITING_TIME)
        TopBarViewInteraction.onTopBarView()
            .performOpenMoreOptions()
        Espresso.onView(ViewMatchers.withText(R.string.menu_new_image))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.discard_button_text))
            .perform(ViewActions.click())
        launchActivityRule.finishActivity()
        launchActivityRule.launchActivity(intent)
        onDrawingSurfaceView()
            .checkPixelColor(Color.TRANSPARENT, BitmapLocationProvider.MIDDLE)
    }
}
