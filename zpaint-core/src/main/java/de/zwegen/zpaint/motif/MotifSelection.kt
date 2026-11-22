package de.zwegen.zpaint.motif

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** UI-thread selection. Call clear when the source layer changes, including edits of the same size. */
class MotifSelection(context: Context, private val onChanged: () -> Unit) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Mutex()
    private var generation = 0L
    private var pending = 0
    private var objects = MotifObjects()
    private var pixels: IntArray? = null
    private var width = 0
    private var height = 0

    @Volatile var mask: Bitmap? = null
        private set
    val busy: Boolean get() = pending != 0
    var failed: Boolean = false
        private set

    @MainThread
    fun tap(source: Bitmap, x: Int, y: Int) {
        if (source.isRecycled || x !in 0 until source.width || y !in 0 until source.height) return
        if (pixels != null && (source.width != width || source.height != height)) clear()
        val snapshot = try {
            pixels ?: SegmentEngine.snapshot(source).also { pixels = it }
        } catch (_: RuntimeException) {
            failed = true
            onChanged()
            return
        } catch (_: OutOfMemoryError) {
            failed = true
            onChanged()
            return
        }
        width = source.width
        height = source.height
        val normalizedX = x.toFloat() / (width - 1).coerceAtLeast(1)
        val normalizedY = y.toFloat() / (height - 1).coerceAtLeast(1)
        val point = MagicTouchInput.tapIndex(normalizedX, normalizedY)
        val current = generation
        val outputWidth = width
        val outputHeight = height
        pending++
        failed = false
        scope.launch {
            try {
                queue.withLock {
                    val previous = objects
                    val (next, combined) = withContext(Dispatchers.Default) {
                        val next = previous.toggle(point) { SegmentEngine.infer(context, snapshot, point) }
                        ensureActive()
                        next to next.union()?.let { SegmentEngine.expand(it, outputWidth, outputHeight) }
                    }
                    objects = next
                    mask = combined
                    failed = false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) failed = true
            } catch (_: LinkageError) {
                if (current == generation) failed = true
            } catch (_: OutOfMemoryError) {
                if (current == generation) failed = true
            } finally {
                if (current == generation) {
                    pending--
                    onChanged()
                }
            }
        }
        onChanged()
    }

    @MainThread
    fun clear() {
        val changed = busy || failed || mask != null
        generation++
        scope.coroutineContext.cancelChildren()
        objects = MotifObjects()
        pixels = null
        mask = null
        pending = 0
        failed = false
        if (changed) onChanged()
    }
}
