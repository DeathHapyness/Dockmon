package dev.rique.guardiao

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import dev.rique.guardiao.face.FaceView
import dev.rique.guardiao.kiosk.Kiosk
import dev.rique.guardiao.mood.MoodEngine
import dev.rique.guardiao.music.PlayResult
import dev.rique.guardiao.music.Rhythm
import dev.rique.guardiao.music.MusicPoller
import dev.rique.guardiao.music.MusicView
import dev.rique.guardiao.server.EventDetector
import dev.rique.guardiao.server.ServerPoller
import dev.rique.guardiao.settings.Settings
import dev.rique.guardiao.settings.SettingsActivity
import dev.rique.guardiao.panel.History
import dev.rique.guardiao.panel.PanelView
import dev.rique.guardiao.ui.PagerLayout
import dev.rique.guardiao.ui.Theme
import dev.rique.guardiao.ui.Themes
import java.util.Random

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings
    private lateinit var face: FaceView
    private lateinit var pages: PagerLayout
    private lateinit var panel: PanelView
    private val cpuHistory = History(PanelView.HISTORY_SIZE)
    private lateinit var poller: ServerPoller
    private lateinit var music: MusicView
    private lateinit var musicPoller: MusicPoller
    private val rhythm = Rhythm()
    private var nowPlaying: PlayResult = PlayResult.Nothing
    private val barsFrame = object : Runnable {
        override fun run() {
            val np = (nowPlaying as? PlayResult.Playing)?.np
            music.bars = rhythm.frame(SystemClock.uptimeMillis(), np?.isPlaying == true, np?.title.orEmpty())
            handler.postDelayed(this, 33)
        }
    }
    private val detector = EventDetector()
    private val mood = MoodEngine(Random())
    private var lastTouchMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            face.update(mood.state(detector.problems(now), now))
            if (pages.page != PAGE_FACE && now - lastTouchMs > RETURN_MS) pages.showPage(PAGE_FACE)
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
        musicPoller = MusicPoller(settings) { r, cover ->
            nowPlaying = r
            music.show(r, cover)
        }
        music.onControl = { musicPoller.control(it) }
        panel = PanelView(this)
        panel.onThemePicked = {
            settings.theme = it.id
            applyTheme(it)
        }
        applyTheme(Themes.byId(settings.theme))
        pages = PagerLayout(this, listOf(panel, face, music), PAGE_FACE)
        pages.onPageChanged = { page ->
            lastTouchMs = System.currentTimeMillis()
            if (page == PAGE_MUSIC) startMusic() else stopMusic()
        }
        readToken(intent)
        setContentView(pages)

        poller = ServerPoller(settings, detector) { events, snapshot ->
            if (events.isNotEmpty()) mood.onEvents(events, System.currentTimeMillis())
            snapshot?.let { cpuHistory.add(it.cpuPercent.toFloat()) }
            panel.show(snapshot, settings.thresholds(), cpuHistory.values())
        }
    }

    override fun onStart() {
        super.onStart()
        poller.start()
        if (pages.page == PAGE_MUSIC) startMusic()
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
        handler.removeCallbacks(barsFrame)
        handler.post(barsFrame)
    }

    private fun stopMusic() {
        musicPoller.stop()
        handler.removeCallbacks(barsFrame)
    }

    private fun applyTheme(t: Theme) {
        face.theme = t
        music.theme = t
        panel.theme = t
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
        const val RETURN_MS = 60_000L
        const val PAGE_FACE = 1
        const val PAGE_MUSIC = 2
        const val EXTRA_REFRESH_TOKEN = "spotifyRefreshToken"
        const val CORNER_PX = 120f
        const val CORNER_HOLD_MS = 5_000L
    }
}
