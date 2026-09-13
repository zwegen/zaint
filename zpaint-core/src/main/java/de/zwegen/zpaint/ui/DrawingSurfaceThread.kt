package de.zwegen.zpaint.ui

/** Runs the surface render loop until the Android surface is destroyed. */
internal class DrawingSurfaceThread(private val renderFrame: Runnable) {
    @Volatile private var running = false
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        worker = Thread({
            while (running && !Thread.currentThread().isInterrupted) renderFrame.run()
        }, "ZaintDrawingSurface").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        worker?.interrupt()
        worker?.let { thread ->
            try {
                thread.join()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        worker = null
    }
}
