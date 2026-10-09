@file:Suppress("DEPRECATION")

package de.zwegen.zpaint.test.espresso

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint.Cap
import android.net.Uri
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.test.espresso.util.wrappers.BrushToolOptionsViewInteraction.Companion.onBrushToolOptionsView
import de.zwegen.zpaint.test.espresso.util.wrappers.ToolBarViewInteraction
import de.zwegen.zpaint.test.utils.ScreenshotOnFailRule
import de.zwegen.zpaint.tools.ZaintToolKind
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

@RunWith(value = Parameterized::class)
class ToolOptionsIntegrationTest(
        private var toolType: ZaintToolKind?,
        private var toolOptionsShownInitially: Boolean,
        private var hasToolOptionsView: Boolean,
        private val hasStrokeCapOptions: Boolean
) {
    @get:Rule
    var activityTestRule: ActivityTestRule<ZaintEditorActivity> = IntentsTestRule(ZaintEditorActivity::class.java)

    @get:Rule
    var screenshotOnFailRule = ScreenshotOnFailRule()

    private var testImageFile: File? = null
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: ZaintToolKind={0}, ToolOptionsShownInitially={1}, HasToolOptionsView={2}, HasStrokeCapOptions{3}")
        fun data(): Iterable<Array<Any>> {
            return listOf(
                    arrayOf(ZaintToolKind.BRUSH, true, true, true),
                    arrayOf(ZaintToolKind.HAND, false, false, false),
                    arrayOf(ZaintToolKind.ERASER, true, true, true),
                    arrayOf(ZaintToolKind.LINE, true, true, true),
                    arrayOf(ZaintToolKind.ICON, true, true, false),
                    arrayOf(ZaintToolKind.FILL, true, true, false),
                    arrayOf(ZaintToolKind.TEXT, true, true, false),
                    arrayOf(ZaintToolKind.TRANSFORM, true, true, false),
                    arrayOf(ZaintToolKind.CLIPBOARD, true, true, false),
                    arrayOf(ZaintToolKind.PIPETTE, false, false, false),
                    arrayOf(ZaintToolKind.SMUDGE, true, true, true),
            )
        }
    }

    @Before
    fun setUp() {
        try {
            testImageFile = File.createTempFile("ZPaintTest", ".png")
            val bitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888)
            val outputStream: OutputStream = FileOutputStream(testImageFile)
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream))
            outputStream.close()
        } catch (e: IOException) {
            throw AssertionError("Could not create temp file", e)
        }
        val intent = Intent()
        intent.data = Uri.fromFile(testImageFile)
        val resultOK = ActivityResult(Activity.RESULT_OK, intent)
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(resultOK)
    }

    @After
    fun tearDown() { testImageFile?.let { assertTrue(it.delete()) } }

    fun onToolBarView(): ToolBarViewInteraction = ToolBarViewInteraction()

    @Test
    fun testToolOptions() {
        onToolBarView()
                .performSelectTool(toolType!!)
        if (!toolOptionsShownInitially) {
            onToolBarView()
                    .performOpenToolOptionsView()
        }
        if (hasToolOptionsView) {
            onToolBarView().onToolOptionsView()
                    .check(matches(isDisplayed()))
        } else {
            onToolBarView().onToolOptionsView()
                    .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun testShouldNotUnselectAlreadySelectedStrokeTypeWhenSelectedAgain() {
        if (hasStrokeCapOptions) {
            onToolBarView()
                    .performSelectTool(toolType!!)
            if (!toolOptionsShownInitially) {
                onToolBarView()
                        .performOpenToolOptionsView()
            }

            repeat(2) {
                onBrushToolOptionsView().performSelectStrokeCapType(Cap.ROUND)
                onBrushToolOptionsView().checkIfStrokeCapIsChecked(Cap.ROUND)
                onBrushToolOptionsView().checkIfStrokeCapIsNotChecked(Cap.SQUARE)
            }

            repeat(2) {
                onBrushToolOptionsView().performSelectStrokeCapType(Cap.SQUARE)
                onBrushToolOptionsView().checkIfStrokeCapIsChecked(Cap.SQUARE)
                onBrushToolOptionsView().checkIfStrokeCapIsNotChecked(Cap.ROUND)
            }
        }
    }
}
