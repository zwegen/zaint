/*
 * Zaint: Tool interaction policy.
 *
 * This file deliberately keeps the controller's UI decisions independent from
 * tool creation.  It contains no Android or view dependencies, so the active
 * tool flow can be verified as plain Kotlin.
 */
package de.zwegen.zpaint.controller

import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.ZaintToolCatalog

object ToolInteractionPolicy {
    private val toolsRequiringExplicitApply = setOf(
        ZaintToolKind.TEXT,
        ZaintToolKind.TRANSFORM,
        ZaintToolKind.ROTATE,
        ZaintToolKind.ALIGN,
        ZaintToolKind.PERSPECTIVE,
        ZaintToolKind.PLACE,
        ZaintToolKind.IMPORTPNG,
        ZaintToolKind.SELECTION,
        ZaintToolKind.ICON,
        ZaintToolKind.SPEECH_BUBBLE,
        ZaintToolKind.SHADOW,
        ZaintToolKind.LINE,
        ZaintToolKind.FILTER_CONTOURS,
    ) + ZaintToolCatalog.adjustableFilterTools

    // Text, icon, import and speech bubble now create a layer automatically only on overlap.
    // Selection keeps its normal paste action and additionally offers an explicit new-layer paste.
    private val toolsCreatingLayers = setOf(ZaintToolKind.SELECTION)

    val explicitApplyTools: Set<ZaintToolKind>
        get() = toolsRequiringExplicitApply

    fun requiresExplicitApply(toolType: ZaintToolKind): Boolean =
        toolType in toolsRequiringExplicitApply

    fun createsLayer(toolType: ZaintToolKind): Boolean = toolType in toolsCreatingLayers
}
