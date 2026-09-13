package de.zwegen.zpaint.tools.implementation

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.command.ZaintCommandTimeline
import de.zwegen.zpaint.command.implementation.ZaintCommandBatch
import de.zwegen.zpaint.tools.ContextCallback
import de.zwegen.zpaint.tools.ToolPaint
import de.zwegen.zpaint.tools.ZaintToolKind
import de.zwegen.zpaint.tools.Workspace
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.tools.ZaintShadowOptionsPanel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

/** Creates a live shadow preview from the alpha of the active layer. */
class ShadowTool(
    private val options: ZaintShadowOptionsPanel,
    contextCallback: ContextCallback,
    toolOptionsViewController: ZaintToolOptionsController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    idlingResource: CountingIdlingResource,
    commandManager: ZaintCommandTimeline
) : ZaintShapeToolBase(contextCallback, toolOptionsViewController, toolPaint, workspace, idlingResource, commandManager) {
    override val toolType = ZaintToolKind.SHADOW
    override var drawTime: Long = 0L
    private var source = workspace.bitmapOfCurrentLayer
    private var sourceLayerIndex = workspace.currentLayerIndex
    private var objectShadowAvailable = source?.let(::hasObjectWithTransparency) == true
    private var blurRadius = 0
    private var size = ZaintShadowOptionsPanel.DEFAULT_NORMAL_SIZE
    private var normalAngle = ShadowCastDirection.DOWN_RIGHT.angleRadians
    private var blurredMask: Bitmap? = null
    private var blurredMaskSource: Bitmap? = null
    private var blurredMaskRadius = -1
    private var blurredMaskOffsetX = 0
    private var blurredMaskOffsetY = 0
    private var alphaBoundsSource: Bitmap? = null
    private var cachedAlphaBounds: AlphaBounds? = null
    private var castDirection: ShadowCastDirection? = null
    private var castAngle = ShadowCastDirection.DOWN_RIGHT.angleRadians
    private var closedCastMask: Bitmap? = null
    private var closedCastMaskSource: Bitmap? = null
    private var closedCastMaskAngle = Float.NaN
    private var closedCastMaskSize = -1
    private var blurredCastMask: Bitmap? = null
    private var blurredCastMaskSource: Bitmap? = null
    private var blurredCastMaskRadius = -1
    private var blurredCastMaskOffsetX = 0
    private var blurredCastMaskOffsetY = 0
    private var innerDirection: ShadowCastDirection? = null
    private var innerAngle = ShadowCastDirection.DOWN_RIGHT.angleRadians
    private var groundShadow = false
    private var groundInitialized = false
    private var groundCenter = PointF()
    private var groundWidth = 0f
    private var groundHeight = 0f
    private var groundRotation = 0f
    private var groundAction: FloatingBoxAction? = null
    private var groundResizeAction = ResizeAction.NONE
    private val groundTransformUi = FloatingBoxTransformUi(contextCallback, workspace)
    private val groundAngleSnap = FloatingBoxAngleSnap()
    private var lockedGroundSnapAngle: Float? = null
    private var showGroundAngleSnapGuide = false
    private var rawGroundRotation = 0f
    private val groundRotationResolver = FloatingBoxRotation()
    private val groundResizeDelta = FloatingBoxResizeDelta()
    private val groundResizeApplication = FloatingBoxResizeApplication()
    private var innerShadowMask: Bitmap? = null
    private var innerShadowMaskSource: Bitmap? = null
    private var innerShadowMaskAngle = Float.NaN
    private var innerShadowMaskSize = -1
    private var blurredInnerMask: Bitmap? = null
    private var blurredInnerMaskSource: Bitmap? = null
    private var blurredInnerMaskRadius = -1
    private var blurredInnerMaskOffsetX = 0
    private var blurredInnerMaskOffsetY = 0
    /** The surface renders on its own thread while the option controls run on the UI thread. */
    private val previewLock = Any()
    private var activeHandle: ShadowHandle? = null
    private var draggedHandleCoordinate: PointF? = null
    private var handleSnap: ShadowCastDirection? = null
    private val castHandleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }
    private val castHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val castHandleOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }
    private val groundFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val groundHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val groundGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // Every shadow mode starts black with a subtle, 30% opacity.
        toolPaint.color = Color.argb(77, 0, 0, 0)
        options.setCallback(object : ZaintShadowOptionsPanel.Callback {
            override fun onBlurChanged(value: Int) {
                synchronized(previewLock) {
                    if (blurRadius != value) clearBlurredMask()
                    if (blurRadius != value) clearBlurredCastMask()
                    if (blurRadius != value) clearBlurredInnerMask()
                    blurRadius = value
                }
                workspace.invalidate()
            }

            override fun onSizeChanged(value: Int) {
                synchronized(previewLock) {
                    if (size == value) return
                    size = value
                    clearClosedCastMask()
                    clearInnerShadowMask()
                }
                workspace.invalidate()
            }

            override fun onCastDirectionChanged(direction: ShadowCastDirection?) {
                sourceForCurrentLayer()
                if (direction != null && !objectShadowAvailable) {
                    options.showShadowUnavailableInfo()
                    return
                }
                synchronized(previewLock) {
                    castDirection = direction
                    direction?.let { castAngle = it.angleRadians }
                    clearClosedCastMask()
                }
                workspace.invalidate()
            }

            override fun onInnerDirectionChanged(direction: ShadowCastDirection?) {
                sourceForCurrentLayer()
                if (direction != null && !objectShadowAvailable) {
                    options.showShadowUnavailableInfo()
                    return
                }
                synchronized(previewLock) {
                    innerDirection = direction
                    direction?.let { innerAngle = it.angleRadians }
                    clearInnerShadowMask()
                }
                workspace.invalidate()
            }

            override fun onGroundShadowChanged(enabled: Boolean) {
                sourceForCurrentLayer()
                synchronized(previewLock) {
                    groundShadow = enabled
                    if (enabled) groundInitialized = false
                }
                if (!enabled && !objectShadowAvailable) {
                    options.showShadowUnavailableInfo()
                    workspace.invalidate()
                    return
                }
                workspace.invalidate()
            }
        })
        if (!objectShadowAvailable) options.showShadowUnavailableInfo()
        toolOptionsViewController.showDelayed()
    }

    override fun changePaintColor(color: Int, invalidate: Boolean) { super.changePaintColor(color, invalidate); workspace.invalidate() }
    override fun drawShape(canvas: Canvas) = Unit
    override fun draw(canvas: Canvas) {
        synchronized(previewLock) {
            sourceForCurrentLayer()?.let { bitmap ->
                if (!groundShadow && !objectShadowAvailable) return
                when {
                    groundShadow -> drawGroundShadow(canvas, drawSelection = true)
                    castDirection != null -> {
                        if (activeHandle == ShadowHandle.CAST) {
                            drawFastCastShadowPreview(canvas, bitmap, castAngle)
                        } else {
                            drawClosedCastShadow(canvas, bitmap, castAngle, hideSourceInPreview = true)
                        }
                        drawShadowHandle(canvas, bitmap, castAngle, ZaintShadowOptionsPanel.MAX_SIZE)
                    }
                    innerDirection != null -> {
                        if (activeHandle == ShadowHandle.INNER) {
                            drawFastInnerShadowPreview(canvas, bitmap, innerAngle)
                        } else {
                            drawInnerShadow(canvas, bitmap, innerAngle)
                        }
                        drawShadowHandle(canvas, bitmap, innerAngle, ZaintShadowOptionsPanel.MAX_INNER_SIZE)
                    }
                    else -> {
                        val normalOffset = normalOffset(bitmap, normalAngle)
                        drawShadow(canvas, bitmap, normalOffset.x, normalOffset.y)
                        drawShadowHandle(canvas, bitmap, normalAngle, ZaintShadowOptionsPanel.MAX_SIZE)
                    }
                }
            }
        }
    }
    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        val bitmap = sourceForCurrentLayer() ?: return false
        if (groundShadow) return handleGroundDown(coordinate)
        if (!objectShadowAvailable) return false
        activeHandle = when {
            castDirection != null -> ShadowHandle.CAST
            innerDirection != null -> ShadowHandle.INNER
            castDirection == null && innerDirection == null -> ShadowHandle.NORMAL
            else -> null
        }
        draggedHandleCoordinate = activeHandle?.let { currentHandlePoint(bitmap, it) }
        handleSnap = activeHandle?.let { currentAngleFor(it) }?.let(::snapDirectionAt)
        previousEventCoordinate = coordinate
        return activeHandle != null
    }
    override fun handleMove(coordinate: PointF?, shouldAnimate: Boolean): Boolean {
        coordinate ?: return false
        if (groundShadow) return handleGroundMove(coordinate)
        return moveShadowHandle(coordinate)
    }
    override fun handleUp(coordinate: PointF?): Boolean {
        if (groundShadow) {
            coordinate?.let(::handleGroundMove)
            groundAction = null
            groundResizeAction = ResizeAction.NONE
            resetGroundAngleSnap()
            return true
        }
        coordinate?.let(::moveShadowHandle)
        activeHandle = null
        draggedHandleCoordinate = null
        handleSnap = null
        return true
    }
    override fun handleDownAnimations(coordinate: PointF?) = Unit
    override fun handleUpAnimations(coordinate: PointF?) = Unit
    override fun toolPositionCoordinates(coordinate: PointF) = coordinate

    /** The plain outer shadow must be composed before its selected source layer in the preview. */
    fun previewsBelowSourceLayer(): Boolean {
        sourceForCurrentLayer()
        return objectShadowAvailable && !groundShadow && castDirection == null && innerDirection == null
    }

    /** Draws only the normal-shadow pixels before the selected source layer is composited. */
    fun drawPreviewBelowSourceLayer(canvas: Canvas) {
        synchronized(previewLock) {
            sourceForCurrentLayer()?.let { bitmap ->
                if (!objectShadowAvailable) return
                val normalOffset = normalOffset(bitmap, normalAngle)
                drawShadow(canvas, bitmap, normalOffset.x, normalOffset.y)
            }
        }
    }

    /** Draws the normal-shadow handle after the source layer, so it remains visible and touchable. */
    fun drawPreviewOverlay(canvas: Canvas) {
        synchronized(previewLock) {
            sourceForCurrentLayer()?.let { bitmap ->
                if (!objectShadowAvailable) return
                drawShadowHandle(canvas, bitmap, normalAngle, ZaintShadowOptionsPanel.MAX_SIZE)
            }
        }
    }
    // Every shadow is kept on its own layer. Outer shadows go below the source; inner and ground
    // shadows stay above it so they remain visible.
    override fun onClickOnButton() {
        sourceForCurrentLayer()
        if (!groundShadow && !objectShadowAvailable) {
            options.showShadowUnavailableInfo()
            return
        }
        commitShadow()
    }

    override fun onClickOnNewLayerButton() {
        onClickOnButton()
    }

    private fun commitShadow() {
        sourceForCurrentLayer()
        if (!groundShadow && !objectShadowAvailable) {
            options.showShadowUnavailableInfo()
            return
        }
        val bitmap = source ?: return
        val newLayerPosition = when {
            groundShadow || innerDirection != null -> workspace.currentLayerIndex
            else -> workspace.currentLayerIndex + 1
        }
        val shadow = Bitmap.createBitmap(workspace.width, workspace.height, Bitmap.Config.ARGB_8888).also {
            synchronized(previewLock) {
                val shadowCanvas = Canvas(it)
                when {
                    groundShadow -> drawGroundShadow(shadowCanvas, drawSelection = false)
                    castDirection != null -> drawClosedCastShadow(
                        shadowCanvas,
                        bitmap,
                        castAngle,
                        hideSourceInPreview = false
                    )
                    innerDirection != null -> drawInnerShadow(shadowCanvas, bitmap, innerAngle)
                    else -> normalOffset(bitmap, normalAngle).let { drawShadow(shadowCanvas, bitmap, it.x, it.y) }
                }
            }
        }
        val shadowCommand = commandFactory.createClipboardCommand(
            shadow,
            PointF(workspace.width / 2f, workspace.height / 2f),
            workspace.width.toFloat(),
            workspace.height.toFloat(),
            0f
        )
        commandManager.addCommand(
            ZaintCommandBatch().apply {
                addCommand(commandFactory.createAddEmptyLayerCommand())
                addCommand(shadowCommand)
                addCommand(commandFactory.createReorderLayersCommand(0, newLayerPosition))
            }
        )
        workspace.invalidate()
    }

    private fun drawShadow(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float) {
        val color = toolPaint.color
        if (blurRadius == 0) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(bitmap, x, y, paint)
            return
        }
        val mask = blurredMaskFor(bitmap)
        canvas.drawBitmap(
            mask,
            x + blurredMaskOffsetX,
            y + blurredMaskOffsetY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        )
    }

    /** Draws one freely movable oval. Blur is controlled exclusively by the option above. */
    private fun drawGroundShadow(canvas: Canvas, drawSelection: Boolean) {
        ensureGroundShadowBounds()
        val rect = groundRect()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = toolPaint.color
            if (blurRadius > 0) {
                maskFilter = BlurMaskFilter(blurRadius.toFloat(), BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.save()
        canvas.rotate(groundRotation, groundCenter.x, groundCenter.y)
        canvas.drawOval(rect, shadowPaint)
        if (drawSelection) drawGroundSelection(canvas, rect)
        canvas.restore()
    }

    /** Uses the shared transform controls; the ground shadow keeps only its oval-specific frame. */
    private fun drawGroundSelection(canvas: Canvas, rect: RectF) {
        drawGroundAngleSnapGuide(canvas, rect)
        val stroke = getStrokeWidthForZoom(3f, 1f, 8f)
        groundFramePaint.color = secondaryShapeColor
        groundFramePaint.strokeWidth = stroke
        canvas.drawRect(rect, groundFramePaint)

        groundHandlePaint.color = primaryShapeColor
        canvas.save()
        canvas.translate(rect.centerX(), rect.centerY())
        groundTransformUi.drawResizeMarkers(canvas, rect.width(), rect.height(), 0, groundHandlePaint)
        groundTransformUi.drawRotationArrows(canvas, rect.width(), rect.height())
        canvas.restore()
    }

    private fun ensureGroundShadowBounds() {
        if (groundInitialized) return
        groundWidth = max(MIN_GROUND_WIDTH, workspace.width * DEFAULT_GROUND_WIDTH_RATIO)
        groundHeight = max(MIN_GROUND_HEIGHT, workspace.height * DEFAULT_GROUND_HEIGHT_RATIO)
        groundCenter = PointF(workspace.width / 2f, workspace.height / 2f)
        groundInitialized = true
    }

    private fun groundRect(): RectF = RectF(
        groundCenter.x - groundWidth / 2f,
        groundCenter.y - groundHeight / 2f,
        groundCenter.x + groundWidth / 2f,
        groundCenter.y + groundHeight / 2f
    )

    private fun handleGroundDown(coordinate: PointF): Boolean = synchronized(previewLock) {
        ensureGroundShadowBounds()
        resetGroundAngleSnap()
        rawGroundRotation = groundRotation
        val interaction = groundTransformUi.resolve(
            coordinate.x,
            coordinate.y,
            groundCenter.x,
            groundCenter.y,
            groundWidth,
            groundHeight,
            groundRotation,
            rotationEnabled = true
        )
        groundAction = interaction.action
        groundResizeAction = interaction.resizeAction
        previousEventCoordinate = coordinate
        true
    }

    private fun handleGroundMove(coordinate: PointF): Boolean = synchronized(previewLock) {
        val previous = previousEventCoordinate ?: return@synchronized false
        val deltaX = coordinate.x - previous.x
        val deltaY = coordinate.y - previous.y
        when (groundAction ?: return@synchronized false) {
            FloatingBoxAction.MOVE -> {
                groundCenter.x += deltaX
                groundCenter.y += deltaY
            }
            FloatingBoxAction.ROTATE -> rotateGround(coordinate, deltaX, deltaY)
            FloatingBoxAction.RESIZE -> resizeGround(deltaX, deltaY, groundResizeAction)
            FloatingBoxAction.NONE -> Unit
        }
        previousEventCoordinate = coordinate
        workspace.invalidate()
        true
    }

    private fun rotateGround(coordinate: PointF, deltaX: Float, deltaY: Float) {
        val rawRotation = groundRotationResolver.afterDrag(
            coordinate.x,
            coordinate.y,
            deltaX,
            deltaY,
            groundCenter.x,
            groundCenter.y,
            rawGroundRotation
        )
        rawGroundRotation = rawRotation
        lockedGroundSnapAngle = groundAngleSnap.resolve(rawRotation, lockedGroundSnapAngle)
        showGroundAngleSnapGuide = lockedGroundSnapAngle != null
        groundRotation = lockedGroundSnapAngle ?: rawRotation
    }

    private fun resetGroundAngleSnap() {
        lockedGroundSnapAngle = null
        showGroundAngleSnapGuide = false
    }

    private fun drawGroundAngleSnapGuide(canvas: Canvas, rect: RectF) {
        val snappedAngle = lockedGroundSnapAngle ?: return
        if (!showGroundAngleSnapGuide) return
        val horizontal = snappedAngle == 0f || abs(snappedAngle) == 180f
        canvas.save()
        canvas.translate(rect.centerX(), rect.centerY())
        val start = if (horizontal) {
            groundWorkspacePointToBoxCanvasPoint(0f, groundCenter.y)
        } else {
            groundWorkspacePointToBoxCanvasPoint(groundCenter.x, 0f)
        }
        val stop = if (horizontal) {
            groundWorkspacePointToBoxCanvasPoint(workspace.width.toFloat(), groundCenter.y)
        } else {
            groundWorkspacePointToBoxCanvasPoint(groundCenter.x, workspace.height.toFloat())
        }
        CanvasCenterGuideRenderer.drawGuideLine(
            canvas,
            start.x,
            start.y,
            stop.x,
            stop.y,
            workspace.scale,
            groundGuidePaint
        )
        canvas.restore()
    }

    private fun groundWorkspacePointToBoxCanvasPoint(x: Float, y: Float): PointF {
        val radians = Math.toRadians((-groundRotation).toDouble())
        val deltaX = x - groundCenter.x
        val deltaY = y - groundCenter.y
        return PointF(
            (deltaX * cos(radians) - deltaY * sin(radians)).toFloat(),
            (deltaX * sin(radians) + deltaY * cos(radians)).toFloat()
        )
    }

    private fun resizeGround(deltaX: Float, deltaY: Float, action: ResizeAction) {
        val movement = groundResizeDelta.resolve(
            deltaX, deltaY, groundRotation, action, groundWidth, groundHeight, keepAspectRatioOnCorners = false
        )
        val resized = groundResizeApplication.apply(
            FloatingBoxResizeState(groundCenter.x, groundCenter.y, groundWidth, groundHeight),
            movement,
            action,
            workspace.width * MAXIMUM_BORDER_RATIO,
            workspace.height * MAXIMUM_BORDER_RATIO,
            limitBorderRatio = true
        )
        groundCenter.x = resized.centerX
        groundCenter.y = resized.centerY
        groundWidth = resized.width.coerceAtLeast(MIN_GROUND_WIDTH)
        groundHeight = resized.height.coerceAtLeast(MIN_GROUND_HEIGHT)
    }

    /**
     * Like a text box, the shadow handle follows the finger by its movement delta. This lets a
     * finger start anywhere without making the handle jump to that first touch point.
     */
    private fun moveShadowHandle(coordinate: PointF): Boolean {
        val previous = previousEventCoordinate ?: return false
        val handleCoordinate = draggedHandleCoordinate ?: return false
        handleCoordinate.offset(coordinate.x - previous.x, coordinate.y - previous.y)
        when (activeHandle) {
            ShadowHandle.CAST -> updateCastFromHandle(handleCoordinate)
            ShadowHandle.INNER -> updateInnerFromHandle(handleCoordinate)
            ShadowHandle.NORMAL -> updateNormalFromHandle(handleCoordinate)
            null -> Unit
        }
        previousEventCoordinate = coordinate
        return true
    }

    /**
     * At 100%, the normal shadow has moved completely beyond the nearest lower or right canvas
     * edge. This is based on the source alpha, rather than on the full bitmap dimensions.
     */
    private fun normalOffset(bitmap: Bitmap, angle: Float): PointF {
        val distance = shadowHandleMaximumDistance(castHandleAnchor(bitmap), bitmap, angle) *
            (size / ZaintShadowOptionsPanel.MAX_SIZE.toFloat())
        return PointF(cos(angle) * distance, sin(angle) * distance)
    }

    /**
     * The handle starts at the centre of the visible source and moves toward the canvas edge.
     * Its angle is free between diagonal snap points; its distance selects 0–100%.
     */
    private fun updateCastFromHandle(coordinate: PointF) {
        val update = handleUpdate(coordinate, ZaintShadowOptionsPanel.MAX_SIZE) ?: return
        castAngle = update.angle
        clearClosedCastMask()
        options.setCastFromHandle(update.nearestSnap, update.size)
        workspace.invalidate()
    }

    private fun updateNormalFromHandle(coordinate: PointF) {
        val update = handleUpdate(coordinate, ZaintShadowOptionsPanel.MAX_SIZE) ?: return
        normalAngle = update.angle
        options.setNormalFromHandle(update.size)
        workspace.invalidate()
    }

    private fun updateInnerFromHandle(coordinate: PointF) {
        val update = handleUpdate(coordinate, ZaintShadowOptionsPanel.MAX_INNER_SIZE) ?: return
        innerAngle = update.angle
        clearInnerShadowMask()
        options.setInnerFromHandle(update.nearestSnap, update.size)
        workspace.invalidate()
    }

    private fun handleUpdate(coordinate: PointF, maximumSize: Int): HandleUpdate? = synchronized(previewLock) {
        val bitmap = sourceForCurrentLayer() ?: return@synchronized null
        val anchor = castHandleAnchor(bitmap)
        val deltaX = coordinate.x - anchor.x
        val deltaY = coordinate.y - anchor.y
        if (hypot(deltaX.toDouble(), deltaY.toDouble()) < MIN_CAST_HANDLE_MOVEMENT) return@synchronized null
        val rawAngle = atan2(deltaY, deltaX)
        val angle = resolveHandleAngle(rawAngle)
        val maximumDistance = shadowHandleMaximumDistance(anchor, bitmap, angle)
        val projectedDistance = hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
        val newSize = if (maximumDistance <= 0f) 0 else {
            (projectedDistance / maximumDistance * maximumSize).roundToInt().coerceIn(0, maximumSize)
        }
        HandleUpdate(angle, newSize, ShadowCastDirection.nearestToAngle(angle))
    }

    /** Locks at a diagonal within 4°, then keeps that lock until the drag exceeds 6°. */
    private fun resolveHandleAngle(rawAngle: Float): Float {
        val locked = handleSnap
        if (locked != null && ShadowCastDirection.angularDistance(rawAngle, locked.angleRadians) <= SNAP_RELEASE_RADIANS) {
            return locked.angleRadians
        }
        val closest = ShadowCastDirection.nearestToAngle(rawAngle)
        return if (ShadowCastDirection.angularDistance(rawAngle, closest.angleRadians) <= SNAP_ENGAGE_RADIANS) {
            handleSnap = closest
            closest.angleRadians
        } else {
            handleSnap = null
            rawAngle
        }
    }

    private fun snapDirectionAt(angle: Float): ShadowCastDirection? =
        ShadowCastDirection.nearestToAngle(angle).takeIf {
            ShadowCastDirection.angularDistance(angle, it.angleRadians) <= SNAP_ENGAGE_RADIANS
        }

    private fun currentAngleFor(handle: ShadowHandle): Float = when (handle) {
        ShadowHandle.CAST -> castAngle
        ShadowHandle.NORMAL -> normalAngle
        ShadowHandle.INNER -> innerAngle
    }

    private fun currentHandlePoint(bitmap: Bitmap, handle: ShadowHandle): PointF = shadowHandlePoint(
        castHandleAnchor(bitmap),
        bitmap,
        currentAngleFor(handle),
        if (handle == ShadowHandle.INNER) ZaintShadowOptionsPanel.MAX_INNER_SIZE else ZaintShadowOptionsPanel.MAX_SIZE
    )

    private fun drawShadowHandle(canvas: Canvas, bitmap: Bitmap, angle: Float, maximumSize: Int) {
        val anchor = castHandleAnchor(bitmap)
        val point = shadowHandlePoint(anchor, bitmap, angle, maximumSize)
        val lineWidth = getStrokeWidthForZoom(2f, 1f, 4f)
        val radius = getInverselyProportionalSizeForZoom(CAST_HANDLE_RADIUS)
        val snapped = snapDirectionAt(angle) != null
        castHandleLinePaint.strokeWidth = lineWidth + getStrokeWidthForZoom(2f, 1f, 3f)
        canvas.drawLine(anchor.x, anchor.y, point.x, point.y, castHandleLinePaint)
        castHandleLinePaint.color = if (snapped) SNAP_GUIDE_COLOR else Color.WHITE
        castHandleLinePaint.strokeWidth = lineWidth
        canvas.drawLine(anchor.x, anchor.y, point.x, point.y, castHandleLinePaint)
        castHandleLinePaint.color = Color.BLACK
        castHandleOutlinePaint.strokeWidth = lineWidth
        canvas.drawCircle(point.x, point.y, radius, castHandlePaint)
        canvas.drawCircle(point.x, point.y, radius, castHandleOutlinePaint)
    }

    private fun castHandleAnchor(bitmap: Bitmap): PointF {
        val bounds = alphaBoundsFor(bitmap)
        return PointF((bounds.left + bounds.right) / 2f, (bounds.top + bounds.bottom) / 2f)
    }

    private fun shadowHandlePoint(anchor: PointF, bitmap: Bitmap, angle: Float, maximumSize: Int): PointF {
        val maximumDistance = shadowHandleMaximumDistance(anchor, bitmap, angle)
        val distance = maximumDistance * (size / maximumSize.toFloat())
        return PointF(
            anchor.x + cos(angle) * distance,
            anchor.y + sin(angle) * distance
        )
    }

    private fun shadowHandleMaximumDistance(anchor: PointF, bitmap: Bitmap, angle: Float): Float {
        val directionX = cos(angle)
        val directionY = sin(angle)
        val distanceX = when {
            directionX > 0f -> (bitmap.width - 1 - anchor.x) / directionX
            directionX < 0f -> anchor.x / -directionX
            else -> Float.POSITIVE_INFINITY
        }
        val distanceY = when {
            directionY > 0f -> (bitmap.height - 1 - anchor.y) / directionY
            directionY < 0f -> anchor.y / -directionY
            else -> Float.POSITIVE_INFINITY
        }
        return minOf(distanceX, distanceY).coerceAtLeast(0f)
    }

    private fun drawClosedCastShadow(
        canvas: Canvas,
        bitmap: Bitmap,
        angle: Float,
        hideSourceInPreview: Boolean
    ) {
        val layerSave = if (hideSourceInPreview) {
            canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), null)
        } else {
            -1
        }
        val mask = closedCastMaskFor(bitmap, angle)
        if (blurRadius == 0) {
            canvas.drawBitmap(
                mask,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(toolPaint.color, PorterDuff.Mode.SRC_IN)
                }
            )
        } else {
            canvas.drawBitmap(
                blurredCastMaskFor(mask),
                blurredCastMaskOffsetX.toFloat(),
                blurredCastMaskOffsetY.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = toolPaint.color }
            )
        }
        if (hideSourceInPreview) {
            canvas.drawBitmap(
                bitmap,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
            )
            canvas.restoreToCount(layerSave)
        }
    }

    /**
     * Lightweight drag preview for a cast shadow. It uses a small, fixed number of copies instead
     * of constructing a new full-resolution alpha mask on every touch event. The exact continuous
     * cast is rendered as soon as the handle is released.
     */
    private fun drawFastCastShadowPreview(canvas: Canvas, bitmap: Bitmap, angle: Float) {
        // The copies form one opaque mask first. The requested opacity is applied only when this
        // layer is restored, so overlap cannot make the preview darker than the final shadow.
        val layerSave = canvas.saveLayer(
            0f,
            0f,
            canvas.width.toFloat(),
            canvas.height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = Color.alpha(toolPaint.color) }
        )
        val anchor = castHandleAnchor(bitmap)
        val distance = shadowHandleMaximumDistance(anchor, bitmap, angle) * (size / 100f)
        for (sample in 1..FAST_CAST_PREVIEW_SAMPLES) {
            val progress = sample / FAST_CAST_PREVIEW_SAMPLES.toFloat()
            drawOpaqueShadow(
                canvas,
                bitmap,
                cos(angle) * distance * progress,
                sin(angle) * distance * progress
            )
        }
        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
        )
        canvas.restoreToCount(layerSave)
    }

    /** Draws a fully opaque copy for the union mask used by [drawFastCastShadowPreview]. */
    private fun drawOpaqueShadow(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float) {
        val opaqueColor = toolPaint.color or Color.BLACK
        if (blurRadius == 0) {
            canvas.drawBitmap(
                bitmap,
                x,
                y,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(opaqueColor, PorterDuff.Mode.SRC_IN)
                }
            )
            return
        }
        val mask = blurredMaskFor(bitmap)
        canvas.drawBitmap(
            mask,
            x + blurredMaskOffsetX,
            y + blurredMaskOffsetY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = opaqueColor }
        )
    }

    /** Draws the dark edge inside the source silhouette, never outside it. */
    private fun drawInnerShadow(canvas: Canvas, bitmap: Bitmap, angle: Float) {
        val save = canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), null)
        val mask = innerShadowMaskFor(bitmap, angle)
        if (blurRadius == 0) {
            canvas.drawBitmap(
                mask,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(toolPaint.color, PorterDuff.Mode.SRC_IN)
                }
            )
        } else {
            canvas.drawBitmap(
                blurredInnerMaskFor(mask),
                blurredInnerMaskOffsetX.toFloat(),
                blurredInnerMaskOffsetY.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = toolPaint.color }
            )
        }
        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
        )
        canvas.restoreToCount(save)
    }

    /** Uses two compositing operations while dragging instead of reading every bitmap pixel. */
    private fun drawFastInnerShadowPreview(canvas: Canvas, bitmap: Bitmap, angle: Float) {
        val save = canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), null)
        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(toolPaint.color, PorterDuff.Mode.SRC_IN)
            }
        )
        val bounds = alphaBoundsFor(bitmap)
        val depth = minOf(bounds.width(), bounds.height()) / 2f *
            (size / ZaintShadowOptionsPanel.MAX_INNER_SIZE.toFloat())
        canvas.drawBitmap(
            bitmap,
            -cos(angle) * depth,
            -sin(angle) * depth,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
        )
        canvas.restoreToCount(save)
    }

    /** Produces a continuous, parallel extrusion of the source alpha. */
    private fun closedCastMaskFor(bitmap: Bitmap, angle: Float): Bitmap {
        if (closedCastMaskSource === bitmap &&
            closedCastMaskAngle == angle &&
            closedCastMaskSize == size
        ) {
            return requireNotNull(closedCastMask)
        }
        clearClosedCastMask()
        return createParallelCastMask(bitmap, angle, size).also { mask ->
            closedCastMask = mask
            closedCastMaskSource = bitmap
            closedCastMaskAngle = angle
            closedCastMaskSize = size
        }
    }

    /**
     * Renders the extrusion as the union of translated alpha silhouettes. Unlike the old
     * diagonal ray walk, each translated sample uses a floating-point vector, so every angle
     * between the snap points is represented by the shadow itself as well as by the handle.
     */
    private fun createParallelCastMask(
        bitmap: Bitmap,
        angle: Float,
        size: Int
    ): Bitmap {
        val bounds = alphaBoundsFor(bitmap)
        val anchor = castHandleAnchor(bitmap)
        val distance = shadowHandleMaximumDistance(anchor, bitmap, angle) * (size / 100f)
        // A copy for every pixel of a long cast blocks the drawing thread.  A bounded number
        // keeps the preview responsive even on large canvases; filtered copies make the steps
        // visually continuous at normal zoom.
        val samples = distance.roundToInt().coerceIn(1, MAX_CAST_SAMPLES)
        val sourceRect = Rect(bounds.left, bounds.top, bounds.right + 1, bounds.bottom + 1)
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        for (sample in 0..samples) {
            val progress = sample / samples.toFloat()
            val offsetX = cos(angle) * distance * progress
            val offsetY = sin(angle) * distance * progress
            canvas.drawBitmap(
                bitmap,
                sourceRect,
                RectF(
                    sourceRect.left + offsetX,
                    sourceRect.top + offsetY,
                    sourceRect.right + offsetX,
                    sourceRect.bottom + offsetY
                ),
                paint
            )
        }
        return mask
    }

    /**
     * Builds the part of the source alpha that faces the selected direction. Shifting the
     * silhouette in that direction and subtracting it leaves a closed inner edge.
     */
    private fun innerShadowMaskFor(bitmap: Bitmap, angle: Float): Bitmap {
        if (innerShadowMaskSource === bitmap &&
            innerShadowMaskAngle == angle &&
            innerShadowMaskSize == size
        ) {
            return requireNotNull(innerShadowMask)
        }
        clearInnerShadowMask()
        val width = bitmap.width
        val height = bitmap.height
        val sourcePixels = IntArray(width * height)
        bitmap.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        val bounds = alphaBoundsFor(bitmap, sourcePixels)
        val maximumDepth = minOf(bounds.width(), bounds.height()) / 2
        val offset = (
            maximumDepth * (size / ZaintShadowOptionsPanel.MAX_INNER_SIZE.toFloat())
        ).roundToInt()
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val sourceAlpha = sourcePixels[index] ushr 24
                val shiftedX = (x + cos(angle) * offset).roundToInt()
                val shiftedY = (y + sin(angle) * offset).roundToInt()
                val shiftedAlpha = if (shiftedX in 0 until width && shiftedY in 0 until height) {
                    sourcePixels[shiftedY * width + shiftedX] ushr 24
                } else {
                    0
                }
                pixels[index] = (sourceAlpha - shiftedAlpha).coerceAtLeast(0) shl 24
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { mask ->
            mask.setPixels(pixels, 0, width, 0, 0, width, height)
            innerShadowMask = mask
            innerShadowMaskSource = bitmap
            innerShadowMaskAngle = angle
            innerShadowMaskSize = size
        }
    }

    private data class AlphaBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = (right - left + 1).coerceAtLeast(0)
        fun height(): Int = (bottom - top + 1).coerceAtLeast(0)
    }

    /** Refreshes the cached layer snapshot whenever the user selects another layer. */
    private fun sourceForCurrentLayer(): Bitmap? = synchronized(previewLock) {
        val currentLayerIndex = workspace.currentLayerIndex
        if (currentLayerIndex == sourceLayerIndex) return@synchronized source

        sourceLayerIndex = currentLayerIndex
        source = workspace.bitmapOfCurrentLayer
        objectShadowAvailable = source?.let(::hasObjectWithTransparency) == true
        alphaBoundsSource = null
        cachedAlphaBounds = null
        clearBlurredMask()
        clearClosedCastMask()
        clearInnerShadowMask()
        activeHandle = null
        handleSnap = null
        groundInitialized = false
        source
    }

    /** Object shadows need both visible pixels and a transparent background on this layer. */
    private fun hasObjectWithTransparency(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var containsObject = false
        var containsTransparency = false
        for (pixel in pixels) {
            if (pixel ushr 24 == 0) {
                containsTransparency = true
            } else {
                containsObject = true
            }
            if (containsObject && containsTransparency) return true
        }
        return false
    }

    private fun alphaBounds(pixels: IntArray, width: Int, height: Int): AlphaBounds {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            if (pixel ushr 24 == 0) return@forEachIndexed
            val x = index % width
            val y = index / width
            left = minOf(left, x)
            top = minOf(top, y)
            right = max(right, x)
            bottom = max(bottom, y)
        }
        return if (right < left || bottom < top) AlphaBounds(0, 0, 0, 0) else AlphaBounds(left, top, right, bottom)
    }

    private fun alphaBoundsFor(bitmap: Bitmap, pixels: IntArray? = null): AlphaBounds {
        if (alphaBoundsSource === bitmap) {
            return requireNotNull(cachedAlphaBounds)
        }
        val sourcePixels = pixels ?: IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
        return alphaBounds(sourcePixels, bitmap.width, bitmap.height).also { bounds ->
            alphaBoundsSource = bitmap
            cachedAlphaBounds = bounds
        }
    }


    /**
     * BlurMaskFilter does not reliably blur a bitmap draw. Blur its alpha mask instead, then
     * tint that mask with the selected shadow color.
     */
    private fun blurredMaskFor(bitmap: Bitmap): Bitmap {
        if (blurredMaskSource === bitmap && blurredMaskRadius == blurRadius) {
            return requireNotNull(blurredMask)
        }
        clearBlurredMask()
        val offsets = IntArray(2)
        blurredMask = bitmap.extractAlpha(
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = BlurMaskFilter(blurRadius.toFloat(), BlurMaskFilter.Blur.NORMAL)
            },
            offsets
        )
        blurredMaskSource = bitmap
        blurredMaskRadius = blurRadius
        blurredMaskOffsetX = offsets[0]
        blurredMaskOffsetY = offsets[1]
        return requireNotNull(blurredMask)
    }

    private fun clearBlurredMask() {
        blurredMask?.takeUnless(Bitmap::isRecycled)?.recycle()
        blurredMask = null
        blurredMaskSource = null
        blurredMaskRadius = -1
        blurredMaskOffsetX = 0
        blurredMaskOffsetY = 0
    }

    private fun blurredCastMaskFor(bitmap: Bitmap): Bitmap {
        if (blurredCastMaskSource === bitmap && blurredCastMaskRadius == blurRadius) {
            return requireNotNull(blurredCastMask)
        }
        clearBlurredCastMask()
        val offsets = IntArray(2)
        blurredCastMask = bitmap.extractAlpha(
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = BlurMaskFilter(blurRadius.toFloat(), BlurMaskFilter.Blur.NORMAL)
            },
            offsets
        )
        blurredCastMaskSource = bitmap
        blurredCastMaskRadius = blurRadius
        blurredCastMaskOffsetX = offsets[0]
        blurredCastMaskOffsetY = offsets[1]
        return requireNotNull(blurredCastMask)
    }

    private fun clearBlurredCastMask() {
        blurredCastMask?.takeUnless(Bitmap::isRecycled)?.recycle()
        blurredCastMask = null
        blurredCastMaskSource = null
        blurredCastMaskRadius = -1
        blurredCastMaskOffsetX = 0
        blurredCastMaskOffsetY = 0
    }

    private fun blurredInnerMaskFor(bitmap: Bitmap): Bitmap {
        if (blurredInnerMaskSource === bitmap && blurredInnerMaskRadius == blurRadius) {
            return requireNotNull(blurredInnerMask)
        }
        clearBlurredInnerMask()
        val offsets = IntArray(2)
        blurredInnerMask = bitmap.extractAlpha(
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = BlurMaskFilter(blurRadius.toFloat(), BlurMaskFilter.Blur.NORMAL)
            },
            offsets
        )
        blurredInnerMaskSource = bitmap
        blurredInnerMaskRadius = blurRadius
        blurredInnerMaskOffsetX = offsets[0]
        blurredInnerMaskOffsetY = offsets[1]
        return requireNotNull(blurredInnerMask)
    }

    private fun clearBlurredInnerMask() {
        blurredInnerMask?.takeUnless(Bitmap::isRecycled)?.recycle()
        blurredInnerMask = null
        blurredInnerMaskSource = null
        blurredInnerMaskRadius = -1
        blurredInnerMaskOffsetX = 0
        blurredInnerMaskOffsetY = 0
    }

    private fun clearInnerShadowMask() {
        clearBlurredInnerMask()
        innerShadowMask?.takeUnless(Bitmap::isRecycled)?.recycle()
        innerShadowMask = null
        innerShadowMaskSource = null
        innerShadowMaskAngle = Float.NaN
        innerShadowMaskSize = -1
    }

    private fun clearClosedCastMask() {
        clearBlurredCastMask()
        closedCastMask?.takeUnless(Bitmap::isRecycled)?.recycle()
        closedCastMask = null
        closedCastMaskSource = null
        closedCastMaskAngle = Float.NaN
        closedCastMaskSize = -1
    }

    private companion object {
        const val CAST_HANDLE_RADIUS = 10f
        const val MIN_CAST_HANDLE_MOVEMENT = 2.0
        const val SNAP_ENGAGE_RADIANS = 4f * Math.PI.toFloat() / 180f
        const val SNAP_RELEASE_RADIANS = 6f * Math.PI.toFloat() / 180f
        const val SNAP_GUIDE_COLOR = 0xff00a8ff.toInt()
        const val FAST_CAST_PREVIEW_SAMPLES = 24
        /** Prevents an excessive amount of preview work on unusually large images. */
        const val MAX_CAST_SAMPLES = 192
        const val DEFAULT_GROUND_WIDTH_RATIO = 0.45f
        const val DEFAULT_GROUND_HEIGHT_RATIO = 0.12f
        const val MIN_GROUND_WIDTH = 24f
        const val MIN_GROUND_HEIGHT = 10f
    }

    private data class HandleUpdate(val angle: Float, val size: Int, val nearestSnap: ShadowCastDirection)
    private enum class ShadowHandle { CAST, NORMAL, INNER }
}
