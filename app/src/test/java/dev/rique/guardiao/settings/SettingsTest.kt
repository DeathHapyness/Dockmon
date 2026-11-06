package dev.rique.guardiao.settings

import android.content.SharedPreferences
import dev.rique.guardiao.server.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {
    @Test
    fun settings_defaults_and_roundtrip() {
        val s = Settings(FakePrefs())
        assertEquals("http://192.168.15.6:61208", s.glancesUrl)
        assertEquals(85.0, s.cpuPercent, 0.0)
        assertEquals(90.0, s.diskPercent, 0.0)
        assertEquals(80.0, s.defaultTempC, 0.0)
        assertEquals(0.15f, s.brightness, 0f)
        assertEquals("fd9157f81f6f4468b6e5b6875418abf5", s.spotifyClientId)
        assertNull(s.spotifyRefreshToken)
        assertNull(s.detectorState)
        assertEquals(Thresholds(85.0, 60_000, 90.0, 80.0), s.thresholds())

        s.glancesUrl = "http://10.0.0.2:61208"
        s.cpuPercent = 70.0
        assertEquals("http://10.0.0.2:61208", s.glancesUrl)
        assertEquals(Thresholds(70.0, 60_000, 90.0, 80.0), s.thresholds())
    }
}

private class FakePrefs : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, defValue: String?) = map[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = map[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in map
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { map[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { map[key] = values }
        override fun putInt(key: String, value: Int) = apply { map[key] = value }
        override fun putLong(key: String, value: Long) = apply { map[key] = value }
        override fun putFloat(key: String, value: Float) = apply { map[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { map[key] = value }
        override fun remove(key: String) = apply { map.remove(key) }
        override fun clear() = apply { map.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
