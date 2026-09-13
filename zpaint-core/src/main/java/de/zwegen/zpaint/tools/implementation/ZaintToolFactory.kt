package de.zwegen.zpaint.tools.implementation

import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.colorpicker.OnColorPickedListener
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolFactory
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.ZaintToolCatalog
import de.zwegen.zpaint.tools.options.BrushPreset
import de.zwegen.zpaint.tools.options.BrushPreset.PENCIL
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.tools.ZaintBrushOptionsPanel
import de.zwegen.zpaint.ui.tools.DefaultBorderToolOptionsView
import de.zwegen.zpaint.ui.tools.ZaintFillOptionsPanel
import de.zwegen.zpaint.ui.tools.DefaultFilterToolOptionsView
import de.zwegen.zpaint.ui.tools.DefaultIconToolOptionsView
import de.zwegen.zpaint.ui.tools.DefaultZaintClipboardOptions
import de.zwegen.zpaint.ui.tools.DefaultImportToolOptionsView
import de.zwegen.zpaint.ui.tools.DefaultPixelToolOptionsView
import de.zwegen.zpaint.ui.tools.DefaultReplaceToolOptionsView
import de.zwegen.zpaint.ui.tools.DefaultRotateToolOptionsView
import de.zwegen.zpaint.ui.tools.DefaultSelectionToolOptionsView
import de.zwegen.zpaint.ui.tools.ZaintTextOptionsPanel
import de.zwegen.zpaint.ui.tools.ZaintTransformOptionsPanel
import de.zwegen.zpaint.ui.tools.ZaintSmudgeOptionsPanel
import de.zwegen.zpaint.ui.tools.ZaintSpeechBubbleOptionsPanel
import de.zwegen.zpaint.ui.tools.ZaintShadowOptionsPanel
import de.zwegen.zpaint.ui.tools.ZaintWarpOptionsPanel
import de.zwegen.zpaint.ui.viewholder.BottomNavigationViewHolder

private const val DRAW_TIME_INIT: Long = 30_000_000

@SuppressWarnings("LongMethod")
class ZaintToolFactory(
    private val bottomNavigationViewHolder: BottomNavigationViewHolder,
    private val onPipetteSelected: () -> Unit,
    private val onLineSelected: () -> Unit = {},
    private val onBrushPresetSelected: (BrushPreset) -> Unit = {},
    private val onFillColorPickerRequested: () -> Unit,
    private val onFillImagePickerRequested: () -> Unit,
    private val colorChangeTarget: ColorChangeTarget,
    private val addToColorHistory: (Int) -> Unit
) : ToolFactory {
    override fun createTool(
        toolType: ZaintToolKind,
        toolOptionsViewController: ZaintToolOptionsController,
        commandManager: ZaintCommandTimeline,
        workspace: Workspace,
        idlingResource: CountingIdlingResource,
        toolPaint: ToolPaint,
        contextCallback: ContextCallback,
        onColorPickedListener: OnColorPickedListener
    ): Tool {
        val toolLayout = toolOptionsViewController.toolSpecificOptionsLayout
        if (toolType == ZaintToolKind.FILTER_CONTOURS) {
            return FilterTool(
                DefaultFilterToolOptionsView(toolOptionsViewController.toolSpecificOptionsTopLayout),
                toolType,
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                onColorPickedListener
            )
        }
        if (ZaintToolCatalog.isFilter(toolType)) {
            return createFilterTool(
                toolType,
                toolLayout,
                toolOptionsViewController,
                contextCallback,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                onColorPickedListener
            )
        }
        return when (toolType) {
            ZaintToolKind.CLIPBOARD -> ZaintClipboardTool(
                DefaultZaintClipboardOptions(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.SELECTION -> ZaintSelectionTool(
                DefaultSelectionToolOptionsView(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.IMPORTPNG -> ZaintImportTool(
                DefaultImportToolOptionsView(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.PIPETTE -> ZaintPipetteTool(
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                onColorPickedListener,
                colorChangeTarget,
                addToColorHistory
            )
            ZaintToolKind.FILL -> ZaintFillTool(
                ZaintFillOptionsPanel(
                    toolLayout,
                    toolOptionsViewController.toolSpecificOptionsTopLayout,
                    onFillColorPickerRequested
                ),
                onFillImagePickerRequested,
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.TRANSFORM -> ZaintTransformTool(
                ZaintTransformOptionsPanel(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.BORDER -> BorderTool(
                DefaultBorderToolOptionsView(toolOptionsViewController.toolSpecificOptionsTopLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.PERSPECTIVE -> PerspectiveTool(
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.PLACE -> PlaceTool(
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.ROTATE -> RotateTool(
                DefaultRotateToolOptionsView(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.ALIGN -> AlignTool(
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.ICON -> IconTool(
                DefaultIconToolOptionsView(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.SPEECH_BUBBLE -> SpeechBubbleTool(
                ZaintSpeechBubbleOptionsPanel(
                    toolOptionsViewController.toolSpecificOptionsTopLayout,
                    toolLayout
                ),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.SHADOW -> ShadowTool(
                ZaintShadowOptionsPanel(
                    toolOptionsViewController.toolSpecificOptionsTopLayout,
                    toolLayout
                ),
                contextCallback, toolOptionsViewController, toolPaint, workspace, idlingResource, commandManager
            )
            ZaintToolKind.ERASER -> ZaintEraserTool(
                ZaintBrushOptionsPanel(
                    toolLayout,
                    showCaps = false,
                    showPreview = false
                ),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT,
                bottomNavigationViewHolder = bottomNavigationViewHolder
            )
            ZaintToolKind.LINE -> ZaintLineTool(
                ZaintBrushOptionsPanel(
                    toolLayout,
                    showPresets = true,
                    showCaps = false,
                    showPreview = false,
                    onBrushPresetSelected = onBrushPresetSelected,
                    onPipetteSelected = onPipetteSelected
                ),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.TEXT -> ZaintTextTool(
                ZaintTextOptionsPanel(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.HAND -> ZaintHandTool(
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT
            )
            ZaintToolKind.SMUDGE -> ZaintSmudgeTool(
                ZaintSmudgeOptionsPanel(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
            )
            ZaintToolKind.WARP -> WarpTool(
                ZaintWarpOptionsPanel(toolOptionsViewController.toolSpecificOptionsTopLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.PIXEL -> PixelTool(
                DefaultPixelToolOptionsView(toolLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            ZaintToolKind.REPLACE -> ReplaceTool(
                DefaultReplaceToolOptionsView(toolOptionsViewController.toolSpecificOptionsTopLayout),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager
            )
            else -> ZaintBrushTool(
                ZaintBrushOptionsPanel(
                    toolLayout,
                    showPresets = true,
                    onLineSelected = onLineSelected
                ),
                contextCallback,
                toolOptionsViewController,
                toolPaint,
                workspace,
                idlingResource,
                commandManager,
                DRAW_TIME_INIT,
                initialBrushPreset = PENCIL,
                useSharedBrushPresetState = true,
                bottomNavigationViewHolder = bottomNavigationViewHolder,
                onPipetteSelected = onPipetteSelected,
                addToColorHistory = addToColorHistory
            )
        }
    }

    private fun createFilterTool(
        toolType: ZaintToolKind,
        toolLayout: android.view.ViewGroup,
        toolOptionsViewController: ZaintToolOptionsController,
        contextCallback: ContextCallback,
        toolPaint: ToolPaint,
        workspace: Workspace,
        idlingResource: CountingIdlingResource,
        commandManager: ZaintCommandTimeline,
        onColorPickedListener: OnColorPickedListener
    ): FilterTool = FilterTool(
        if (ZaintToolCatalog.hasAdjustableFilterOptions(toolType)) {
            DefaultFilterToolOptionsView(toolLayout)
        } else {
            null
        },
        toolType,
        contextCallback,
        toolOptionsViewController,
        toolPaint,
        workspace,
        idlingResource,
        commandManager,
        onColorPickedListener
    )
}
