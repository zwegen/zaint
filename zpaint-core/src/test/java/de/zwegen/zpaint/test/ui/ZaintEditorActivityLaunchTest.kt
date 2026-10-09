package de.zwegen.zpaint.test.ui

import de.zwegen.zpaint.ZaintEditorActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZaintEditorActivityLaunchTest {
    @Test
    fun launchCreatesTheEditorView() {
        Robolectric.buildActivity(ZaintEditorActivity::class.java).setup().get()
    }
}
