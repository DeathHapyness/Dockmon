package dev.rique.guardiao

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import dev.rique.guardiao.face.FaceView
import dev.rique.guardiao.kiosk.Kiosk
import dev.rique.guardiao.mood.MoodEngine
import dev.rique.guardiao.music.MicCapture
import dev.rique.guardiao.music.MusicPoller
import dev.rique.guardiao.music.MusicView
import dev.rique.guardiao.server.EventDetector
import dev.rique.guardiao.server.ServerPoller
import dev.rique.guardiao.settings.Settings
import dev.rique.guardiao.settings.SettingsActivity
import dev.rique.guardiao.ui.TwoPageLayout
import java.util.Random

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings
    private lateinit var face: FaceView
    private lateinit var pages: TwoPageLayout
    private lateinit var poller: ServerPoller
    private lateinit var music: MusicView
    private lateinit var musicPoller: MusicPoller
    private lateinit var mic: MicCapture
    private val detector = EventDetector()
    private val mood = MoodEngine(Random())
    private var lastTouchMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            face.update(mood.state(detector.problems(now), now))
            if (pages.page == 1 && now - lastTouchMs > MUSIC_TIMEOUT_MS) pages.showPage(0)
            handler.postDelayed(this, 100)
        }
    }

    private val openSettings = Runnable { startActivity(Intent(this, SettingsActivity::class.java)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(getSharedPreferences("guardiao", MODE_PRIVATE))
        settings.detectorState?.let { runCatching { detector.importState(it) } }
        detector.thresholds = settings.thresholds()

        face = FaceView(this)
        music = MusicView(this)
        musicPoller = MusicPoller(settings) { r, cover -> music.show(r, cover) }
        music.onControl = { musicPoller.control(it) }
        mic = MicCapture { music.bars = it }
        pages = TwoPageLayout(this, face, music)
        pages.onPageChanged = { page ->
            lastTouchMs = System.currentTimeMillis()
            if (page == 1) startMusic() else stopMusic()
        }
        readToken(intent)
        setContentView(pages)

        poller = ServerPoller(settings, detector) { events ->
            if (events.isNotEmpty()) mood.onEvents(events, System.currentTimeMillis())
        }
    }

    override fun onStart() {
        super.onStart()
        poller.start()
        if (pages.page == 1) startMusic()
        handler.post(tick)
    }

    override fun onResume() {
        super.onResume()
        Kiosk.apply(this, settings.brightness)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readToken(intent)
    }

    private fun startMusic() {
        musicPoller.start()
        mic.start()
    }

    private fun stopMusic() {
        musicPoller.stop()
        mic.stop()
    }

    private fun readToken(intent: Intent?) {
        intent?.getStringExtra(EXTRA_REFRESH_TOKEN)?.let { settings.spotifyRefreshToken = it }
    }

    override fun onStop() {
        poller.stop()
        stopMusic()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(openSettings)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Kiosk.apply(this, settings.brightness)
    }

    @Deprecated("Back does nothing on a dedicated device")
    override fun onBackPressed() {}

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchMs = System.currentTimeMillis()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                if (ev.x < CORNER_PX && ev.y < CORNER_PX) handler.postDelayed(openSettings, CORNER_HOLD_MS)
            MotionEvent.ACTION_MOVE ->
                if (ev.x >= CORNER_PX || ev.y >= CORNER_PX) handler.removeCallbacks(openSettings)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(openSettings)
        }
        return super.dispatchTouchEvent(ev)
    }

    private companion object {
        const val MUSIC_TIMEOUT_MS = 60_000L
        const val EXTRA_REFRESH_TOKEN = "spotifyRefreshToken"
        const val CORNER_PX = 120f
        const val CORNER_HOLD_MS = 5_000L
    }
}
