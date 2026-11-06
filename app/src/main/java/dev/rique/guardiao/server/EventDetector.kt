package dev.rique.guardiao.server

import org.json.JSONArray
import org.json.JSONObject

sealed class ServerEvent {
    data class Down(val names: List<String>) : ServerEvent()
    data class Up(val names: List<String>) : ServerEvent()
    data class New(val names: List<String>) : ServerEvent()
}

sealed class Problem(val severity: Int) {
    data class PhoneHot(val celsius: Double) : Problem(70)
    data class TempCritical(val label: String, val celsius: Double) : Problem(60)
    data class BatteryLow(val percent: Int) : Problem(if (percent <= 10) 55 else 35)
    data class ContainersDown(val names: List<String>) : Problem(50)
    data class DiskFull(val mount: String, val percent: Double) : Problem(40)
    data class TempWarning(val label: String, val celsius: Double) : Problem(30)
    data class CpuHigh(val percent: Double) : Problem(20)
    data class Unhealthy(val names: List<String>) : Problem(10)
    data object Unreachable : Problem(5)
}

data class Thresholds(
    val cpuPercent: Double = 85.0,
    val cpuSustainMs: Long = 60_000,
    val diskPercent: Double = 90.0,
    val defaultTempC: Double = 80.0,
)

class EventDetector(var thresholds: Thresholds = Thresholds()) {
    private class Track(var status: String, var downReads: Int, var confirmedDown: Boolean, var lastSeenMs: Long)

    private val tracks = linkedMapOf<String, Track>()
    private var initialized = false
    private var cpuHighSinceMs = -1L
    private var lastCpu = 0.0
    private var lastFs = emptyList<FsInfo>()
    private var lastSensors = emptyList<SensorInfo>()
    private var lastSuccessMs = -1L
    private var lastFailureMs = -1L

    fun onSnapshot(s: ServerSnapshot, nowMs: Long): List<ServerEvent> {
        lastSuccessMs = nowMs
        lastFailureMs = -1L
        lastCpu = s.cpuPercent
        lastFs = s.fs
        lastSensors = s.sensors
        cpuHighSinceMs = if (s.cpuPercent >= thresholds.cpuPercent) {
            if (cpuHighSinceMs < 0) nowMs else cpuHighSinceMs
        } else -1L

        val seen = s.containers.associateBy { it.name }
        if (!initialized) {
            initialized = true
            for (c in s.containers) {
                val down = !isUp(c.status) && c.status != UNHEALTHY
                tracks[c.name] = Track(c.status, if (down) 2 else 0, down, nowMs)
            }
            return emptyList()
        }

        val downs = mutableListOf<String>()
        val ups = mutableListOf<String>()
        val news = mutableListOf<String>()

        for (c in s.containers) {
            val t = tracks[c.name]
            if (t == null) {
                news += c.name
                val down = !isUp(c.status) && c.status != UNHEALTHY
                tracks[c.name] = Track(c.status, if (down) 2 else 0, down, nowMs)
                continue
            }
            t.lastSeenMs = nowMs
            t.status = c.status
            if (isUp(c.status) || c.status == UNHEALTHY) {
                if (t.confirmedDown) ups += c.name
                t.downReads = 0
                t.confirmedDown = false
            } else {
                registerDownRead(t, c.name, downs)
            }
        }

        val iterator = tracks.entries.iterator()
        while (iterator.hasNext()) {
            val (name, t) = iterator.next()
            if (name in seen) continue
            if (nowMs - t.lastSeenMs >= FORGET_MS) {
                iterator.remove()
                continue
            }
            t.status = ""
            registerDownRead(t, name, downs)
        }

        return buildList {
            if (downs.isNotEmpty()) add(ServerEvent.Down(downs))
            if (ups.isNotEmpty()) add(ServerEvent.Up(ups))
            if (news.isNotEmpty()) add(ServerEvent.New(news))
        }
    }

    fun onFailure(nowMs: Long) {
        if (lastFailureMs < 0) lastFailureMs = nowMs
    }

    fun problems(nowMs: Long): List<Problem> {
        val out = mutableListOf<Problem>()
        if (lastFailureMs >= 0) {
            val silentSince = if (lastSuccessMs >= 0) lastSuccessMs else lastFailureMs
            if (nowMs - silentSince >= UNREACHABLE_MS) out += Problem.Unreachable
        }

        for (sensor in lastSensors) {
            val critical = sensor.critical
            val warning = sensor.warning ?: thresholds.defaultTempC
            if (critical != null && sensor.celsius >= critical) out += Problem.TempCritical(sensor.label, sensor.celsius)
            else if (sensor.celsius >= warning) out += Problem.TempWarning(sensor.label, sensor.celsius)
        }

        val down = tracks.filterValues { it.confirmedDown }.keys.toList()
        if (down.isNotEmpty()) out += Problem.ContainersDown(down)

        lastFs.filter { it.percent >= thresholds.diskPercent }.forEach { out += Problem.DiskFull(it.mount, it.percent) }

        if (cpuHighSinceMs >= 0 && nowMs - cpuHighSinceMs >= thresholds.cpuSustainMs) out += Problem.CpuHigh(lastCpu)

        val unhealthy = tracks.filterValues { it.status == UNHEALTHY }.keys.toList()
        if (unhealthy.isNotEmpty()) out += Problem.Unhealthy(unhealthy)

        return out.sortedByDescending { it.severity }
    }

    fun exportState(): String {
        val arr = JSONArray()
        for ((name, t) in tracks) {
            arr.put(
                JSONObject()
                    .put("name", name)
                    .put("status", t.status)
                    .put("downReads", t.downReads)
                    .put("confirmedDown", t.confirmedDown)
                    .put("lastSeenMs", t.lastSeenMs),
            )
        }
        return JSONObject().put("tracks", arr).toString()
    }

    fun importState(json: String) {
        val arr = JSONObject(json).optJSONArray("tracks") ?: return
        tracks.clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            tracks[o.getString("name")] = Track(
                o.optString("status"),
                o.optInt("downReads"),
                o.optBoolean("confirmedDown"),
                o.optLong("lastSeenMs"),
            )
        }
        initialized = true
    }

    private fun registerDownRead(t: Track, name: String, downs: MutableList<String>) {
        if (t.confirmedDown) return
        t.downReads++
        if (t.downReads >= 2) {
            t.confirmedDown = true
            downs += name
        }
    }

    private fun isUp(status: String) = status == "running" || status == "healthy"

    private companion object {
        const val UNHEALTHY = "unhealthy"
        const val FORGET_MS = 600_000L
        const val UNREACHABLE_MS = 30_000L
    }
}
