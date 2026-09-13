package de.zwegen.zpaint.command.implementation

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import de.zwegen.zpaint.command.Command
import de.zwegen.zpaint.command.ZaintCommandFactoryApi
import de.zwegen.zpaint.command.ColorChangeTarget
import de.zwegen.zpaint.command.serialization.ZaintStrokePath
import de.zwegen.zpaint.command.serialization.ZaintTextStyle
import de.zwegen.zpaint.command.snapshot.CommandInputSnapshot
import de.zwegen.zpaint.common.CommonFactory
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.implementation.FilterArea
import de.zwegen.zpaint.tools.implementation.FilterType
import de.zwegen.zpaint.tools.implementation.SmudgePoint
import de.zwegen.zpaint.tools.implementation.SmudgeStrokeBounds
import de.zwegen.zpaint.tools.helper.DefaultFillAlgorithmFactory

class ZaintCommandFactory : ZaintCommandFactoryApi {
    private val commonFactory = CommonFactory()
    private val inputSnapshot = CommandInputSnapshot()

    override fun createInitCommand(width: Int, height: Int): Command =
        createDocumentInitialization(width, height, ZaintAddLayerCommand(commonFactory))

    override fun createInitCommand(bitmap: Bitmap): Command =
        createDocumentInitialization(bitmap.width, bitmap.height, ZaintBitmapDocumentLoad(bitmap))

    override fun createInitCommand(layers: List<ZaintLayerContracts.ZaintLayer>): Command = layers.first().bitmap.let { bitmap ->
        createDocumentInitialization(bitmap.width, bitmap.height, ZaintLayerStackDocumentLoad(layers))
    }

    override fun createResetCommand(): Command = ZaintCommandBatch().apply {
        addCommand(ZaintDocumentReset())
        addCommand(ZaintAddLayerCommand(commonFactory))
    }

    private fun createDocumentInitialization(width: Int, height: Int, content: Command): Command =
        ZaintCommandBatch().apply {
            addCommand(ZaintDocumentDimensions(width, height))
            addCommand(content)
        }

    override fun createAddEmptyLayerCommand(): Command = ZaintAddLayerCommand(commonFactory)

    override fun createDuplicateLayerCommand(position: Int): Command = ZaintDuplicateLayerCommand(position)

    override fun createSelectLayerCommand(position: Int): Command = ZaintSelectLayerCommand(position)

    override fun createLayerOpacityCommand(position: Int, opacityPercentage: Int): Command =
        ZaintLayerOpacityChange(position, opacityPercentage)

    override fun createRemoveLayerCommand(index: Int): Command = ZaintRemoveLayerCommand(index)

    override fun createReorderLayersCommand(position: Int, swapWith: Int): Command =
        ZaintLayerReorder(position, swapWith)

    override fun createMergeLayersCommand(position: Int, mergeWith: Int): Command =
        ZaintLayerMerge(position, mergeWith)

    override fun createMergeActiveLayersCommand(activeLayerPositions: List<Int>): Command =
        ZaintCommandBatch().apply {
            var destinationPosition = activeLayerPositions.last()
            activeLayerPositions.dropLast(1).asReversed().forEach { sourcePosition ->
                addCommand(ZaintLayerMerge(sourcePosition, destinationPosition))
                destinationPosition--
            }
        }

    override fun createRotateCommand(rotateDirection: ZaintRotationDirection, currentLayerOnly: Boolean): Command =
        ZaintDocumentRotation(rotateDirection, currentLayerOnly)

    override fun createFlipCommand(flipDirection: ZaintMirrorDirection, currentLayerOnly: Boolean): Command =
        ZaintLayerMirror(flipDirection, currentLayerOnly)

    override fun createCropCommand(
        resizeCoordinateXLeft: Int,
        resizeCoordinateYTop: Int,
        resizeCoordinateXRight: Int,
        resizeCoordinateYBottom: Int,
        maximumBitmapResolution: Int
    ): Command = ZaintDocumentCrop(
        resizeCoordinateXLeft,
        resizeCoordinateYTop,
        resizeCoordinateXRight,
        resizeCoordinateYBottom,
        maximumBitmapResolution
    )

    override fun createPointCommand(paint: Paint, coordinate: PointF): Command = ZaintPointCommand(
        inputSnapshot.paint(paint),
        inputSnapshot.point(coordinate)
    )

    override fun createFillCommand(x: Int, y: Int, paint: Paint, colorTolerance: Float): Command =
        ZaintFloodFill(
            DefaultFillAlgorithmFactory(),
            inputSnapshot.point(x, y),
            inputSnapshot.paint(paint),
            colorTolerance
        )

    override fun createGradientFillCommand(
        x: Int,
        y: Int,
        paint: Paint,
        colorTolerance: Float,
        colors: IntArray,
        direction: FillGradientDirection
    ): Command =
        GradientFillCommand(
            commonFactory.createPoint(x, y),
            commonFactory.createPaint(paint),
            colorTolerance,
            colors,
            direction
        )

    override fun createPathCommand(paint: Paint, path: ZaintStrokePath): Command = ZaintStrokeCommand(
        inputSnapshot.paint(paint),
        inputSnapshot.path(path)
    )

    override fun createSmudgePathCommand(
        bitmap: Bitmap,
        pointPath: MutableList<PointF>,
        maxPressure: Float,
        maxSize: Float,
        minSize: Float
    ): Command = ZaintSmudgeStroke(
        inputSnapshot.bitmap(bitmap),
        inputSnapshot.points(pointPath),
        maxPressure,
        maxSize,
        minSize
    )

    override fun createSmudgePathCommand(
        bitmap: Bitmap,
        pointPath: MutableList<PointF>,
        maxPressure: Float,
        maxSize: Float,
        minSize: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): Command = ZaintSmudgeStroke(
        inputSnapshot.bitmap(bitmap),
        inputSnapshot.points(pointPath),
        maxPressure,
        maxSize,
        minSize,
        SmudgeStrokeBounds.calculate(
            canvasWidth,
            canvasHeight,
            pointPath.map { SmudgePoint(it.x, it.y) },
            maxSize
        )
    )

    override fun createWarpStrokeCommand(
        bitmap: Bitmap,
        pointPath: List<PointF>,
        radius: Float,
        strength: Int
    ): Command = ZaintWarpStroke(
        bitmap,
        inputSnapshot.points(pointPath),
        radius,
        strength
    )

    private fun copySmudgePath(pointPath: MutableList<PointF>): MutableList<PointF> =
        pointPath.mapTo(mutableListOf()) { commonFactory.createPointF(it) }

    override fun createTextToolCommand(
        multilineText: Array<String>,
        textPaint: Paint,
        boxOffset: Int,
        boxWidth: Float,
        boxHeight: Float,
        toolPosition: PointF,
        boxRotation: Float,
        curvatureDegrees: Float,
        typefaceInfo: ZaintTextStyle
    ): Command = ZaintTextStamp(
        multilineText, commonFactory.createPaint(textPaint),
        boxOffset.toFloat(), boxWidth, boxHeight, inputSnapshot.point(toolPosition),
        boxRotation, curvatureDegrees, typefaceInfo
    )

    override fun createResizeCommand(newWidth: Int, newHeight: Int): Command =
        ZaintDocumentResize(newWidth, newHeight)

    override fun createBorderCommand(borderAmount: Int, borderColor: Int, maximumBitmapResolution: Int): Command =
        BorderCommand(borderAmount, borderColor, maximumBitmapResolution)

    override fun createClipboardCommand(
        bitmap: Bitmap,
        toolPosition: PointF,
        boxWidth: Float,
        boxHeight: Float,
        boxRotation: Float
    ): Command = ZaintClipboardStamp(
        bitmap,
        inputSnapshot.pixelPoint(toolPosition),
        boxWidth,
        boxHeight,
        boxRotation
    )

    override fun createCutCommand(
        toolPosition: PointF,
        boxWidth: Float,
        boxHeight: Float,
        boxRotation: Float
    ): Command = ZaintSelectionClear(inputSnapshot.pixelPoint(toolPosition), boxWidth, boxHeight, boxRotation)

    override fun createColorChangedCommand(
        tool: Tool?,
        target: ColorChangeTarget,
        color: Int
    ): Command =
        ZaintColorChange(tool, target, color)

    override fun createFilterCommand(filterType: FilterType, value: Int): Command =
        FilterCommand(filterType, value)

    override fun createAreaFilterCommand(
        filterType: FilterType,
        value: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command =
        AreaFilterCommand(filterType, value, centerX, centerY, radius)

    override fun createAreaFilterCommand(filterType: FilterType, value: Int, area: FilterArea): Command =
        AreaFilterCommand(filterType, value, area)

    override fun createSplashCommand(selectedColors: IntArray, tolerances: IntArray): Command =
        SplashCommand(selectedColors, tolerances)

    override fun createAreaSplashCommand(selectedColors: IntArray, tolerances: IntArray, areas: List<FilterArea>): Command =
        AreaSplashCommand(selectedColors, tolerances, areas)

    override fun createRecolorCommand(sourceColor: Int, targetColor: Int, tolerance: Int): Command =
        RecolorCommand(sourceColor, targetColor, tolerance)

    override fun createAreaRecolorCommand(
        sourceColor: Int,
        targetColor: Int,
        tolerance: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command =
        AreaRecolorCommand(sourceColor, targetColor, tolerance, centerX, centerY, radius)

    override fun createBlurAreaCommand(
        value: Int,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command =
        BlurAreaCommand(value, centerX, centerY, radius)

    override fun createPixelateCommand(
        pixelSize: Int,
        areaMode: Boolean,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Command =
        PixelateCommand(pixelSize, areaMode, centerX, centerY, radius)

    override fun createReplaceCommand(
        path: ZaintStrokePath,
        points: List<PointF>,
        brushSize: Float,
        sourceCenter: PointF
    ): Command =
        ReplaceCommand(
            ZaintStrokePath(path),
            points.map { PointF(it.x, it.y) },
            brushSize,
            PointF(sourceCenter.x, sourceCenter.y)
        )

    override fun createPerspectiveCommand(
        sourcePoints: FloatArray,
        maximumBitmapResolution: Int
    ): Command =
        PerspectiveCommand(sourcePoints.copyOf(), maximumBitmapResolution)

    override fun createPlaceLayerCommand(deltaX: Float, deltaY: Float, rotationDegrees: Float, scaleFactor: Float): Command =
        PlaceLayerCommand(deltaX, deltaY, rotationDegrees, scaleFactor)
}
