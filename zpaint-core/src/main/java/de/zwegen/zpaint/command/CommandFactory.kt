package de.zwegen.zpaint.command

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import de.zwegen.zpaint.command.implementation.ZaintMirrorDirection
import de.zwegen.zpaint.command.implementation.FillGradientDirection
import de.zwegen.zpaint.command.implementation.ZaintRotationDirection
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.command.serialization.ZaintTextStyle
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.implementation.FilterArea
import de.zwegen.zpaint.tools.implementation.FilterType

/**
 * Zaint's command creation boundary.
 *
 * Keeping command construction behind this interface prevents UI tools from depending on a
 * particular history or bitmap implementation.
 */
interface ZaintCommandFactoryApi {
    fun createInitCommand(width: Int, height: Int): Command

    fun createInitCommand(bitmap: Bitmap): Command

    fun createInitCommand(layers: List<ZaintLayerContracts.ZaintLayer>): Command

    fun createResetCommand(): Command

    fun createAddEmptyLayerCommand(): Command

    fun createDuplicateLayerCommand(position: Int): Command

    fun createSelectLayerCommand(position: Int): Command

    fun createLayerOpacityCommand(position: Int, opacityPercentage: Int): Command

    fun createRemoveLayerCommand(index: Int): Command

    fun createReorderLayersCommand(position: Int, swapWith: Int): Command

    fun createMergeLayersCommand(position: Int, mergeWith: Int): Command

    fun createMergeActiveLayersCommand(activeLayerPositions: List<Int>): Command

    fun createRotateCommand(rotateDirection: ZaintRotationDirection, currentLayerOnly: Boolean = false): Command

    fun createFlipCommand(flipDirection: ZaintMirrorDirection, currentLayerOnly: Boolean = true): Command

    fun createCropCommand(
        resizeCoordinateXLeft: Int,
        resizeCoordinateYTop: Int,
        resizeCoordinateXRight: Int,
        resizeCoordinateYBottom: Int,
        maximumBitmapResolution: Int
    ): Command

    fun createPointCommand(paint: Paint, coordinate: PointF): Command

    fun createFillCommand(x: Int, y: Int, paint: Paint, colorTolerance: Float): Command

    fun createGradientFillCommand(
        x: Int,
        y: Int,
        paint: Paint,
        colorTolerance: Float,
        colors: IntArray,
        direction: FillGradientDirection
    ): Command

    fun createPathCommand(paint: Paint, path: ZaintStrokePath): Command

    fun createSmudgePathCommand(
        bitmap: Bitmap,
        pointPath: MutableList<PointF>,
        maxPressure: Float,
        maxSize: Float,
        minSize: Float
    ): Command

    fun createSmudgePathCommand(
        bitmap: Bitmap,
        pointPath: MutableList<PointF>,
        maxPressure: Float,
        maxSize: Float,
        minSize: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): Command

    /** Creates one undoable deformation stroke from the current layer bitmap. */
    fun createWarpStrokeCommand(
        bitmap: Bitmap,
        pointPath: List<PointF>,
        radius: Float,
        strength: Int
    ): Command

    fun createTextToolCommand(
        multilineText: Array<String>,
        textPaint: Paint,
        boxOffset: Int,
        boxWidth: Float,
        boxHeight: Float,
        toolPosition: PointF,
        boxRotation: Float,
        curvatureDegrees: Float,
        typefaceInfo: ZaintTextStyle
    ): Command

    fun createResizeCommand(newWidth: Int, newHeight: Int): Command

    fun createBorderCommand(borderAmount: Int, borderColor: Int, maximumBitmapResolution: Int): Command

    fun createClipboardCommand(
        bitmap: Bitmap,
        toolPosition: PointF,
        boxWidth: Float,
        boxHeight: Float,
        boxRotation: Float
    ): Command

    fun createCutCommand(
        toolPosition: PointF,
        boxWidth: Float,
        boxHeight: Float,
        boxRotation: Float
    ): Command

    fun createColorChangedCommand(tool: Tool?, target: ColorChangeTarget, color: Int): Command

    fun createFilterCommand(filterType: FilterType, value: Int): Command

    fun createAreaFilterCommand(
        filterType: FilterType,
        value: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command

    fun createAreaFilterCommand(filterType: FilterType, value: Int, area: FilterArea): Command

    fun createSplashCommand(selectedColors: IntArray, tolerances: IntArray): Command

    fun createAreaSplashCommand(selectedColors: IntArray, tolerances: IntArray, areas: List<FilterArea>): Command

    fun createRecolorCommand(
        sourceColor: Int,
        targetColor: Int,
        tolerance: Int
    ): Command

    fun createAreaRecolorCommand(
        sourceColor: Int,
        targetColor: Int,
        tolerance: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command

    fun createBlurAreaCommand(
        value: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command

    fun createPixelateCommand(
        pixelSize: Int,
        areaMode: Boolean,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command

    fun createReplaceCommand(
        path: ZaintStrokePath,
        points: List<PointF>,
        brushSize: Float,
        sourceCenter: PointF
    ): Command

    fun createPerspectiveCommand(
        sourcePoints: FloatArray,
        maximumBitmapResolution: Int
    ): Command

    fun createPlaceLayerCommand(deltaX: Float, deltaY: Float, rotationDegrees: Float, scaleFactor: Float): Command
}
