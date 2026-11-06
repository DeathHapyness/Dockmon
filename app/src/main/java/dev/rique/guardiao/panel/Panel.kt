package dev.rique.guardiao.panel

import dev.rique.guardiao.server.ContainerInfo
import dev.rique.guardiao.server.ServerSnapshot
import dev.rique.guardiao.server.Thresholds
import kotlin.math.roundToInt

data class Gauge(val label: String, val fraction: Float, val text: String, val alert: Boolean)

enum class Status { DOWN, UNHEALTHY, OK }

data class Row(val name: String, val status: Status, val cpuPercent: Double, val memBytes: Long)

object Panel {
    private const val RAM_ALERT = 90.0

    fun gauges(s: ServerSnapshot, t: Thresholds): List<Gauge> {
        val disk = s.fs.maxOfOrNull { it.percent }
        val hottest = s.sensors.maxByOrNull { it.celsius }
        return listOf(
            percent("CPU", s.cpuPercent, s.cpuPercent >= t.cpuPercent),
            percent("RAM", s.memPercent, (s.memPercent ?: 0.0) >= RAM_ALERT),
            percent("DISCO", disk, (disk ?: 0.0) >= t.diskPercent),
            if (hottest == null) Gauge("TEMP", 0f, "—", false)
            else Gauge(
                "TEMP",
                (hottest.celsius / 100).toFloat().coerceIn(0f, 1f),
                "${hottest.celsius.roundToInt()}°",
                hottest.celsius >= (hottest.warning ?: t.defaultTempC),
            ),
        )
    }

    fun rows(containers: List<ContainerInfo>): List<Row> =
        containers.map { Row(it.name, statusOf(it.status), it.cpuPercent, it.memBytes) }
            .sortedWith(compareBy<Row> { it.status.ordinal }.thenBy { it.name.lowercase() })

    private fun statusOf(status: String) = when (status) {
        "running", "healthy" -> Status.OK
        "unhealthy" -> Status.UNHEALTHY
        else -> Status.DOWN
    }

    private fun percent(label: String, v: Double?, alert: Boolean) =
        if (v == null) Gauge(label, 0f, "—", false)
        else Gauge(label, (v / 100).toFloat().coerceIn(0f, 1f), "${v.roundToInt()}%", alert)
}

class History(private val capacity: Int) {
    private val data = ArrayDeque<Float>()

    fun add(v: Float) {
        data.addLast(v)
        while (data.size > capacity) data.removeFirst()
    }

    fun values(): List<Float> = data.toList()
}
