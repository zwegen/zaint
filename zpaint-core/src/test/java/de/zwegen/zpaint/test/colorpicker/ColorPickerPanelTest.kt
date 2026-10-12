package de.zwegen.zpaint.test.colorpicker

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ColorPickerPanelTest {
    private fun panel(initial: Int, history: List<Int>, changes: MutableList<Int>,
                      preserveAlpha: Boolean = false, lockAlpha: Boolean = false): ViewGroup {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(),
            androidx.appcompat.R.style.Theme_AppCompat)
        val constructor = Class.forName("de.zwegen.zpaint.colorpicker.PickerPanel")
            .declaredConstructors.single { it.parameterCount == 7 }.apply { isAccessible = true }
        val changed: (Int) -> Unit = { changes += it }
        val pipette: () -> Unit = {}
        return constructor.newInstance(context, initial, lockAlpha, preserveAlpha, history,
            changed, pipette) as ViewGroup
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()

    private fun preview(panel: ViewGroup): TextView = descendants(panel).filterIsInstance<TextView>()
        .single { it.text.toString().matches(Regex("#[0-9A-F]{8}")) }

    private fun choose(panel: ViewGroup, color: Int) = descendants(panel)
        .single { it.contentDescription?.toString() == String.format("#%08X", color) }.performClick()

    @Test fun choosingHistoryColorsPreservesExactHexValuesWithoutSliderQuantization() {
        val colors = listOf(0xff5090c0.toInt(), 0x80456789.toInt(), 0x7f123456,
            0xffabcdef.toInt(), 0xff2b759e.toInt())
        val changes = mutableListOf<Int>()
        val panel = panel(Color.BLACK, colors, changes)
        for (color in colors) {
            changes.clear()
            assertTrue(choose(panel, color))
            assertEquals(listOf(color), changes)
            assertEquals(String.format("#%08X", color), preview(panel).text.toString())
        }
    }

    @Test fun historySelectionStillPreservesCurrentOpacityWhenRequested() {
        val changes = mutableListOf<Int>()
        val target = 0xff5090c0.toInt()
        val panel = panel(0x44000000, listOf(target), changes, preserveAlpha = true)
        changes.clear()
        choose(panel, target)
        assertEquals(listOf(0x445090c0), changes)
    }

    @Test fun alphaLockedPanelStillHidesTheOpacitySlider() {
        val changes = mutableListOf<Int>()
        val target = 0x805090c0.toInt()
        val panel = panel(Color.BLACK, listOf(target), changes, lockAlpha = true)
        assertEquals(3, descendants(panel).filterIsInstance<SeekBar>().size)
        changes.clear()
        choose(panel, target)
        assertEquals(listOf(target), changes)
    }

    @Test fun programmaticSliderSynchronizationDoesNotModifyTheSelectedColor() {
        val changes = mutableListOf<Int>()
        val initial = 0xff5090c0.toInt()
        val panel = panel(initial, emptyList(), changes)
        changes.clear()
        descendants(panel).filterIsInstance<SeekBar>().first().progress = 120
        assertTrue(changes.isEmpty())
        assertEquals(String.format("#%08X", initial), preview(panel).text.toString())
    }

    @Test fun userSliderChangesStillUpdateTheColor() {
        val changes = mutableListOf<Int>()
        val panel = panel(Color.RED, emptyList(), changes)
        val slider = descendants(panel).filterIsInstance<SeekBar>().first()
        val field = SeekBar::class.java.getDeclaredField("mOnSeekBarChangeListener")
            .apply { isAccessible = true }
        val listener = field.get(slider) as SeekBar.OnSeekBarChangeListener
        changes.clear()
        listener.onProgressChanged(slider, 120, true)
        assertEquals(listOf(Color.GREEN), changes)
    }

    @Test fun tappingPreviewCopiesDisplayedHexWithoutApplyingOrChangingTheColor() {
        val changes = mutableListOf<Int>()
        val initial = 0x805090c0.toInt()
        val panel = panel(initial, emptyList(), changes)
        val preview = preview(panel)
        val field = preview.parent as ViewGroup
        changes.clear()
        assertTrue(field.performClick())
        val clipboard = panel.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(preview.text.toString(), clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals("Hexcode", clipboard.primaryClip!!.description.label.toString())
        assertTrue(changes.isEmpty())
        assertNotNull(field.childrenImageView().drawable)
        assertNotNull(field.childrenImageView().colorFilter)
    }

    @Test fun copyingAfterHistorySelectionUsesTheNewDisplayedHex() {
        val changes = mutableListOf<Int>()
        val target = 0x7f123456
        val panel = panel(Color.BLACK, listOf(target), changes)
        choose(panel, target)
        val preview = preview(panel)
        (preview.parent as View).performClick()
        val clipboard = panel.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("#7F123456", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    private fun ViewGroup.childrenImageView(): ImageView =
        (0 until childCount).map { getChildAt(it) }.filterIsInstance<ImageView>().single()
}
