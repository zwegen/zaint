package de.zwegen.zpaint.test.junit.tools

import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.rule.ActivityTestRule
import de.zwegen.zpaint.ZaintEditorActivity
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.implementation.ZaintFillTool
import de.zwegen.zpaint.tools.options.ZaintFillOptions
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class FillToolTest {
    @Mock
    var fillToolOptions: ZaintFillOptions? = null

    @Mock
    var contextCallback: ContextCallback? = null

    @Mock
    private val fillToolOptionsView: ZaintFillOptions? = null
    @Mock
    var toolOptionsViewController: ZaintToolOptionsController? = null

    @Mock
    var workspace: Workspace? = null

    @Mock
    var toolPaint: ToolPaint? = null

    @Mock
    var commandManager: ZaintCommandTimeline? = null

    @Mock
    var commandFactory: ZaintCommandFactoryApi? = null

    @Mock
    var layerModel: ZaintLayerContracts.Model? = null

    private var toolToTest: ZaintFillTool? = null

    private var idlingResource: CountingIdlingResource? = null

    @Rule
    @JvmField
    var launchActivityRule = ActivityTestRule(ZaintEditorActivity::class.java)
    @Before
    fun setUp() {
        idlingResource = launchActivityRule.activity.idlingResource
        toolToTest = ZaintFillTool(
            fillToolOptionsView!!,
            {},
            contextCallback!!, toolOptionsViewController!!, toolPaint!!, workspace!!,
            idlingResource!!, commandManager!!, 0
        )
    }
    @Test
    fun testShouldReturnCorrectToolType() {
        val toolType = toolToTest?.toolType
        Assert.assertEquals(ZaintToolKind.FILL, toolType)
    }
}
