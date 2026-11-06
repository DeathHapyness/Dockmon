package dev.rique.guardiao

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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
import dev.rique.guardiao.power.BatteryBadge
import dev.rique.guardiao.power.PhoneBattery
import dev.rique.guardiao.server.Problem
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
    private var musicSinceMs = 0L
    private lateinit var badge: BatteryBadge
    private var battery: PhoneBattery? = null
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(PhoneBattery.from(intent))
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            face.update(mood.state(problems(now), now))
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
        face.onTap = { x, y ->
            val now = System.currentTimeMillis()
            mood.onTouch(x, y, now)
            face.update(mood.state(problems(now), now))
        }
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
            val now = System.currentTimeMillis()
            lastTouchMs = now
            if (page == PAGE_MUSIC) {
                musicSinceMs = now
                startMusic()
            } else {
                if (musicSinceMs > 0) mood.onBackFromMusic(now - musicSinceMs, now)
                musicSinceMs = 0
                stopMusic()
            }
        }
        readToken(intent)
        badge = BatteryBadge(this)
        setContentView(FrameLayout(this).apply {
            addView(pages)
            addView(badge)
        })

        poller = ServerPoller(settings, detector) { events, snapshot ->
            if (events.isNotEmpty()) mood.onEvents(events, System.currentTimeMillis())
            snapshot?.let { cpuHistory.add(it.cpuPercent.toFloat()) }
            panel.show(snapshot, settings.thresholds(), cpuHistory.values())
        }
    }

    override fun onStart() {
        super.onStart()
        poller.start()
        onBattery(registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(PhoneBattery::from))
        if (pages.page == PAGE_MUSIC) startMusic()
        handler.post(tick)
    }

    override fun onResume() {
        super.onResume()
        Kiosk.apply(this, brightness())
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

    private fun problems(now: Long): List<Problem> =
        (battery?.problems().orEmpty() + detector.problems(now)).sortedByDescending { it.severity }

    private fun brightness() =
        if (battery?.hot == true) minOf(settings.brightness, PhoneBattery.COOL_DOWN_BRIGHTNESS) else settings.brightness

    private fun onBattery(b: PhoneBattery?) {
        val wasHot = battery?.hot == true
        battery = b
        badge.battery = b
        if ((b?.hot == true) != wasHot) Kiosk.apply(this, brightness())
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
        runCatching { unregisterReceiver(batteryReceiver) }
        poller.stop()
        stopMusic()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(openSettings)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Kiosk.apply(this, brightness())
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
