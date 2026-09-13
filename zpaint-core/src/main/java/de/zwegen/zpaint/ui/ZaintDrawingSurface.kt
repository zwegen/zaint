package de.zwegen.zpaint.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.test.espresso.idling.CountingIdlingResource
import de.zwegen.zpaint.R
import de.zwegen.zpaint.colorpicker.R as ColorPickerR
import de.zwegen.zpaint.contract.ZaintLayerContracts
import de.zwegen.zpaint.listener.DrawingSurfaceListener
import de.zwegen.zpaint.model.ZaintLayerModel
import de.zwegen.zpaint.tools.Tool
import de.zwegen.zpaint.tools.ToolReference
import de.zwegen.zpaint.tools.options.ZaintToolOptionsController
import de.zwegen.zpaint.ui.viewholder.DrawerLayoutViewHolder

/**
 * Android surface responsible solely for displaying the current layer model.
 *
 * Tool input is delegated to [DrawingSurfaceListener]; composition remains in [ZaintLayerModel].
 * Rendering is requested on demand, so an idle canvas does not redraw continuously.
 */
open class ZaintDrawingSurface : SurfaceView, SurfaceHolder.Callback {
    private var renderMonitor: Object? = Object()
    private val imageBounds = Rect()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }
    private val checkerPaint = Paint().apply {
        val checker = BitmapFactory.decodeResource(resources, ColorPickerR.drawable.zpaint_checkeredbg)
        shader = BitmapShader(checker, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val outsideColor = ContextCompat.getColor(context, R.color.zpaint_main_drawing_surface_background)
    private val touchListener: DrawingSurfaceListener

    private var surfaceAvailable = false
    private var renderRequested = false
    private var renderThread: DrawingSurfaceThread? = null
    private lateinit var layerModel: ZaintLayerContracts.Model
    private lateinit var perspective: Perspective
    private lateinit var toolReference: ToolReference
    private lateinit var idlingResource: CountingIdlingResource
    private lateinit var toolOptionsViewController: ZaintToolOptionsController

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?) : super(context)

    init {
        touchListener = DrawingSurfaceListener(object : DrawingSurfaceListener.DrawingSurfaceListenerCallback {
            override fun getCurrentTool(): Tool? = if (::toolReference.isInitialized) toolReference.tool else null
            override fun multiplyPerspectiveScale(factor: Float) = perspective.multiplyScale(factor)
            override fun translatePerspective(x: Float, y: Float) = perspective.translate(x, y)
            override fun convertToCanvasFromSurface(surfacePoint: PointF) = perspective.convertToCanvasFromSurface(surfacePoint)
            override fun getZaintToolOptionsController(): ZaintToolOptionsController =
                toolOptionsViewController
        }, resources.displayMetrics.density)
        setOnTouchListener(touchListener)
    }

    fun setArguments(
        layerModel: ZaintLayerContracts.Model,
        perspective: Perspective,
        toolReference: ToolReference,
        idlingResource: CountingIdlingResource,
        fragmentManager: FragmentManager,
        toolOptionsViewController: ZaintToolOptionsController,
        drawerLayoutViewHolder: DrawerLayoutViewHolder
    ) {
        this.layerModel = layerModel
        this.perspective = perspective
        this.toolReference = toolReference
        this.idlingResource = idlingResource
        this.toolOptionsViewController = toolOptionsViewController
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        holder.addCallback(this)
    }

    override fun onDetachedFromWindow() {
        holder.removeCallback(this)
        super.onDetachedFromWindow()
    }

    fun refreshDrawingSurface() {
        val monitor = renderMonitor ?: return
        synchronized(monitor) {
            renderRequested = true
            monitor.notifyAll()
        }
    }

    @Synchronized
    fun setBitmap(bitmap: Bitmap?) {
        if (bitmap != null && ::layerModel.isInitialized) layerModel.currentLayer?.bitmap = bitmap
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.setFormat(PixelFormat.RGBA_8888)
        renderThread?.stop()
        renderThread = DrawingSurfaceThread(Runnable(::drawWhenRequested))
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!::perspective.isInitialized) return
        surfaceAvailable = true
        perspective.setSurfaceFrame(holder.surfaceFrame)
        if (perspective.callResetScaleAndTransformationOnStartUp < 2) {
            perspective.resetScaleAndTranslation()
            perspective.callResetScaleAndTransformationOnStartUp++
        }
        renderThread?.start()
        refreshDrawingSurface()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceAvailable = false
        val monitor = renderMonitor ?: return
        synchronized(monitor) { monitor.notifyAll() }
        renderThread?.stop()
    }

    private fun drawWhenRequested() {
        val monitor = renderMonitor ?: return
        synchronized(monitor) {
            while (surfaceAvailable && !renderRequested) {
                try {
                    monitor.wait()
                } catch (_: InterruptedException) {
                    return
                }
            }
            if (!surfaceAvailable) return
            renderRequested = false
        }
        if (!::layerModel.isInitialized || !::idlingResource.isInitialized) return

        var canvas: Canvas? = null
        try {
            idlingResource.increment()
            canvas = holder.lockCanvas()
            canvas?.let(::drawFrame)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Could not lock drawing surface", error)
        } finally {
            if (!idlingResource.isIdleNow) idlingResource.decrement()
            canvas?.let(holder::unlockCanvasAndPost)
        }
    }

    private fun drawFrame(canvas: Canvas) {
        synchronized(layerModel) {
            imageBounds.set(0, 0, layerModel.width, layerModel.height)
            canvas.drawColor(outsideColor)
            val saveCount = canvas.save()
            perspective.applyToCanvas(canvas)
            canvas.drawRect(imageBounds, checkerPaint)
            val currentTool = toolReference.tool
            val currentLayerIndex = layerModel.currentLayer?.let(layerModel::getLayerIndexOf)
            (layerModel as ZaintLayerModel).drawLayersOntoCanvasCorrectOrder(
                canvas,
                currentLayerIndex,
                null,
                currentTool
            )
            canvas.drawRect(imageBounds, framePaint)
            canvas.restoreToCount(saveCount)
        }
    }

    private companion object {
        const val TAG = "ZaintDrawingSurface"
    }
}
