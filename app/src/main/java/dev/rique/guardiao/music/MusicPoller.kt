package dev.rique.guardiao.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import dev.rique.guardiao.settings.Settings
import java.io.IOException
import java.net.URL

class MusicPoller(settings: Settings, private val onResult: (PlayResult, Bitmap?) -> Unit) {
    private val api = SpotifyApi(settings)
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var coverUrl: String? = null
    private var cover: Bitmap? = null

    private val poll = object : Runnable {
        override fun run() {
            fetch()
            worker?.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("spotify").also { it.start() }
        thread = t
        worker = Handler(t.looper).also { it.post(poll) }
    }

    fun stop() {
        worker?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        worker = null
    }

    fun control(action: String) {
        val w = worker ?: return
        w.removeCallbacks(poll)
        w.post {
            val error = when (action) {
                "play" -> api.play()
                "pause" -> api.pause()
                "next" -> api.next()
                else -> api.previous()
            }
            if (error != null) main.post { onResult(error, cover) }
        }
        w.postDelayed(poll, 400)
    }

    private fun fetch() {
        val r = api.current()
        val url = (r as? PlayResult.Playing)?.np?.coverUrl
        if (url != coverUrl) {
            coverUrl = url
            cover = url?.let(::download)
        }
        val bmp = cover
        main.post { onResult(r, bmp) }
    }

    private fun download(url: String): Bitmap? = try {
        URL(url).openStream().use { BitmapFactory.decodeStream(it) }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val INTERVAL_MS = 3_000L
    }
}
