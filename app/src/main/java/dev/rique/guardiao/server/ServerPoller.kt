package dev.rique.guardiao.server

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import dev.rique.guardiao.settings.Settings

class ServerPoller(
    private val settings: Settings,
    private val detector: EventDetector,
    private val onUpdate: (List<ServerEvent>, ServerSnapshot?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null

    private val poll = object : Runnable {
        override fun run() {
            val snapshot = GlancesClient(settings.glancesUrl).fetch()
            main.post {
                val now = System.currentTimeMillis()
                detector.thresholds = settings.thresholds()
                if (snapshot == null) {
                    detector.onFailure(now)
                    onUpdate(emptyList(), null)
                } else {
                    val events = detector.onSnapshot(snapshot, now)
                    settings.detectorState = detector.exportState()
                    onUpdate(events, snapshot)
                }
            }
            worker?.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("glances").also { it.start() }
        thread = t
        worker = Handler(t.looper).also { it.post(poll) }
    }

    fun stop() {
        worker?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        worker = null
    }

    private companion object {
        const val INTERVAL_MS = 5_000L
    }
}
