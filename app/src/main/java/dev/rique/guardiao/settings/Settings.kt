package dev.rique.guardiao.settings

import android.content.SharedPreferences
import dev.rique.guardiao.server.Thresholds

class Settings(private val prefs: SharedPreferences) {
    var glancesUrl: String
        get() = prefs.getString("glancesUrl", null) ?: "http://192.168.15.6:61208"
        set(v) = prefs.edit().putString("glancesUrl", v).apply()

    var cpuPercent: Double
        get() = prefs.getFloat("cpuPercent", 85f).toDouble()
        set(v) = prefs.edit().putFloat("cpuPercent", v.toFloat()).apply()

    var diskPercent: Double
        get() = prefs.getFloat("diskPercent", 90f).toDouble()
        set(v) = prefs.edit().putFloat("diskPercent", v.toFloat()).apply()

    var defaultTempC: Double
        get() = prefs.getFloat("defaultTempC", 80f).toDouble()
        set(v) = prefs.edit().putFloat("defaultTempC", v.toFloat()).apply()

    var brightness: Float
        get() = prefs.getFloat("brightness", 0.15f)
        set(v) = prefs.edit().putFloat("brightness", v).apply()

    var spotifyClientId: String?
        get() = prefs.getString("spotifyClientId", null)
        set(v) = prefs.edit().putString("spotifyClientId", v).apply()

    var spotifyRefreshToken: String?
        get() = prefs.getString("spotifyRefreshToken", null)
        set(v) = prefs.edit().putString("spotifyRefreshToken", v).apply()

    var detectorState: String?
        get() = prefs.getString("detectorState", null)
        set(v) = prefs.edit().putString("detectorState", v).apply()

    fun thresholds() = Thresholds(cpuPercent, 60_000, diskPercent, defaultTempC)
}
