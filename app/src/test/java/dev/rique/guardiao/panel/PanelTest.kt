package dev.rique.guardiao.panel

import dev.rique.guardiao.server.ContainerInfo
import dev.rique.guardiao.server.FsInfo
import dev.rique.guardiao.server.SensorInfo
import dev.rique.guardiao.server.ServerSnapshot
import dev.rique.guardiao.server.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTest {
    private val snap = ServerSnapshot(
        containers = listOf(
            ContainerInfo("nginx", "running", 1.0),
            ContainerInfo("immich", "unhealthy", 30.0),
            ContainerInfo("adguard", "healthy", 0.5),
            ContainerInfo("redis", "exited"),
        ),
        cpuPercent = 91.0,
        fs = listOf(FsInfo("/", 40.0), FsInfo("/media", 72.5)),
        sensors = listOf(SensorInfo("Core 0", 55.0, 80.0, 95.0), SensorInfo("Core 1", 61.0, 80.0, 95.0)),
        memPercent = 52.2,
        uptime = "3 days, 4:01:00",
    )

    @Test
    fun gauges_cpu_ram_disk_temp() {
        val g = Panel.gauges(snap, Thresholds())
        assertEquals(listOf("CPU", "RAM", "DISCO", "TEMP"), g.map { it.label })
        assertEquals(listOf("91%", "52%", "73%", "61°"), g.map { it.text })
        assertEquals(0.91f, g[0].fraction, 0.001f)
        assertEquals(0.61f, g[3].fraction, 0.001f)
        assertEquals(listOf(true, false, false, false), g.map { it.alert })
    }

    @Test
    fun gauges_without_optional_data() {
        val g = Panel.gauges(snap.copy(memPercent = null, fs = emptyList(), sensors = emptyList()), Thresholds())
        assertEquals(listOf("91%", "—", "—", "—"), g.map { it.text })
        assertEquals(0f, g[1].fraction, 0f)
    }

    @Test
    fun temp_alert_uses_sensor_warning() {
        val hot = snap.copy(sensors = listOf(SensorInfo("Core 0", 82.0, 80.0, 95.0)))
        assertTrue(Panel.gauges(hot, Thresholds()).last().alert)
        val noLimits = snap.copy(sensors = listOf(SensorInfo("acpi", 79.0, null, null)))
        assertFalse(Panel.gauges(noLimits, Thresholds()).last().alert)
    }

    @Test
    fun containers_problems_first_then_by_name() {
        val rows = Panel.rows(snap.containers)
        assertEquals(listOf("redis", "immich", "adguard", "nginx"), rows.map { it.name })
        assertEquals(listOf(Status.DOWN, Status.UNHEALTHY, Status.OK, Status.OK), rows.map { it.status })
    }

    @Test
    fun history_keeps_last_values() {
        val h = History(3)
        assertEquals(emptyList<Float>(), h.values())
        listOf(1f, 2f, 3f, 4f).forEach(h::add)
        assertEquals(listOf(2f, 3f, 4f), h.values())
    }
}
