package dev.rique.guardiao.music

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.sqrt

class MicCapture(private val onBars: (FloatArray) -> Unit) {
    private val spectrum = Spectrum()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "mic").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop() {
        val rate = spectrum.sampleRate
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, spectrum.fftSize * 4))
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: SecurityException) {
            null
        }
        if (record == null) {
            decorativeLoop()
            return
        }

        val buf = ShortArray(spectrum.fftSize)
        var lastSoundMs = SystemClock.uptimeMillis()
        var lastFrameMs = 0L
        try {
            record.startRecording()
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                val now = SystemClock.uptimeMillis()
                if (rms(buf, n) >= SILENCE_RMS) lastSoundMs = now
                if (now - lastFrameMs < FRAME_MS) continue
                lastFrameMs = now
                val bars = if (now - lastSoundMs > SILENCE_MS) spectrum.decorative(now) else spectrum.process(buf)
                main.post { onBars(bars) }
            }
        } finally {
            record.stop()
            record.release()
        }
    }

    private fun decorativeLoop() {
        while (running) {
            val bars = spectrum.decorative(SystemClock.uptimeMillis())
            main.post { onBars(bars) }
            SystemClock.sleep(FRAME_MS)
        }
    }

    private fun rms(buf: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) {
            val v = buf[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / n).toFloat()
    }

    private companion object {
        const val FRAME_MS = 33L
        const val SILENCE_MS = 5_000L
        const val SILENCE_RMS = 0.01f
    }
}
