package dev.rique.guardiao.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {
    private fun snap(
        vararg c: Pair<String, String>,
        cpu: Double = 10.0,
        fs: List<FsInfo> = emptyList(),
        sensors: List<SensorInfo> = emptyList(),
    ) = ServerSnapshot(c.map { ContainerInfo(it.first, it.second) }, cpu, fs, sensors)

    private fun started(vararg c: Pair<String, String>): EventDetector =
        EventDetector().also { it.onSnapshot(snap(*c), 0) }

    @Test
    fun first_snapshot_emits_nothing() {
        val d = EventDetector()
        assertEquals(emptyList<ServerEvent>(), d.onSnapshot(snap("a" to "running", "b" to "exited"), 0))
    }

    @Test
    fun down_needs_two_consecutive_reads() {
        val d = started("x" to "running")
        assertEquals(emptyList<ServerEvent>(), d.onSnapshot(snap("x" to "exited"), 5_000))
        assertEquals(listOf(ServerEvent.Down(listOf("x"))), d.onSnapshot(snap("x" to "exited"), 10_000))
    }

    @Test
    fun single_bad_read_is_ignored() {
        val d = started("x" to "running")
        d.onSnapshot(snap("x" to "exited"), 5_000)
        assertEquals(emptyList<ServerEvent>(), d.onSnapshot(snap("x" to "running"), 10_000))
        assertEquals(emptyList<ServerEvent>(), d.onSnapshot(snap("x" to "exited"), 15_000))
    }

    @Test
    fun healthy_counts_as_up() {
        val d = started("x" to "healthy")
        d.onSnapshot(snap("x" to "healthy"), 5_000)
        assertEquals(emptyList<ServerEvent>(), d.onSnapshot(snap("x" to "healthy"), 10_000))
        assertTrue(d.problems(10_000).isEmpty())
    }

    @Test
    fun unhealthy_is_problem_not_down() {
        val d = started("x" to "healthy")
        d.onSnapshot(snap("x" to "unhealthy"), 5_000)
        assertEquals(emptyList<ServerEvent>(), d.onSnapshot(snap("x" to "unhealthy"), 10_000))
        assertEquals(listOf(Problem.Unhealthy(listOf("x"))), d.problems(10_000))
    }

    @Test
    fun recovered_emits_up() {
        val d = started("x" to "running")
        d.onSnapshot(snap("x" to "exited"), 5_000)
        d.onSnapshot(snap("x" to "exited"), 10_000)
        assertEquals(listOf(Problem.ContainersDown(listOf("x"))), d.problems(10_000))
        assertEquals(listOf(ServerEvent.Up(listOf("x"))), d.onSnapshot(snap("x" to "running"), 15_000))
        assertTrue(d.problems(15_000).isEmpty())
    }

    @Test
    fun new_container_emits_new() {
        val d = started("x" to "running")
        assertEquals(
            listOf(ServerEvent.New(listOf("y"))),
            d.onSnapshot(snap("x" to "running", "y" to "running"), 5_000),
        )
    }

    @Test
    fun missing_10min_is_forgotten_then_new_on_return() {
        val d = started("x" to "running", "y" to "running")
        d.onSnapshot(snap("y" to "running"), 5_000)
        assertEquals(listOf(ServerEvent.Down(listOf("x"))), d.onSnapshot(snap("y" to "running"), 10_000))
        d.onSnapshot(snap("y" to "running"), 610_000)
        assertTrue(d.problems(610_000).isEmpty())
        assertEquals(
            listOf(ServerEvent.New(listOf("x"))),
            d.onSnapshot(snap("x" to "running", "y" to "running"), 615_000),
        )
    }

    @Test
    fun cpu_high_requires_60s() {
        val d = EventDetector()
        d.onSnapshot(snap(cpu = 90.0), 0)
        assertTrue(d.problems(0).isEmpty())
        d.onSnapshot(snap(cpu = 90.0), 30_000)
        assertTrue(d.problems(30_000).isEmpty())
        d.onSnapshot(snap(cpu = 90.0), 60_000)
        assertEquals(listOf(Problem.CpuHigh(90.0)), d.problems(60_000))
        d.onSnapshot(snap(cpu = 20.0), 65_000)
        assertTrue(d.problems(65_000).isEmpty())
    }

    @Test
    fun disk_full_at_90() {
        val d = EventDetector()
        d.onSnapshot(snap(fs = listOf(FsInfo("/", 89.9), FsInfo("/media", 90.0))), 0)
        assertEquals(listOf(Problem.DiskFull("/media", 90.0)), d.problems(0))
    }

    @Test
    fun temp_uses_sensor_thresholds() {
        val d = EventDetector()
        d.onSnapshot(snap(sensors = listOf(SensorInfo("Core 0", 75.0, 74.0, 94.0))), 0)
        assertEquals(listOf(Problem.TempWarning("Core 0", 75.0)), d.problems(0))
        d.onSnapshot(snap(sensors = listOf(SensorInfo("Core 0", 95.0, 74.0, 94.0))), 5_000)
        assertEquals(listOf(Problem.TempCritical("Core 0", 95.0)), d.problems(5_000))
    }

    @Test
    fun temp_default_80_when_no_threshold() {
        val d = EventDetector()
        d.onSnapshot(snap(sensors = listOf(SensorInfo("acpitz 0", 79.0, null, null))), 0)
        assertTrue(d.problems(0).isEmpty())
        d.onSnapshot(snap(sensors = listOf(SensorInfo("acpitz 0", 80.0, null, null))), 5_000)
        assertEquals(listOf(Problem.TempWarning("acpitz 0", 80.0)), d.problems(5_000))
    }

    @Test
    fun unreachable_after_30s_is_problem_not_scare() {
        val d = started("x" to "running")
        d.onFailure(5_000)
        assertTrue(d.problems(5_000).isEmpty())
        d.onFailure(30_000)
        assertEquals(listOf(Problem.Unreachable), d.problems(30_000))
    }

    @Test
    fun restored_state_does_not_reemit_events() {
        val a = started("x" to "running", "y" to "running")
        a.onSnapshot(snap("x" to "exited", "y" to "running"), 5_000)
        a.onSnapshot(snap("x" to "exited", "y" to "running"), 10_000)
        val b = EventDetector()
        b.importState(a.exportState())
        assertEquals(emptyList<ServerEvent>(), b.onSnapshot(snap("x" to "exited", "y" to "running"), 20_000))
        assertEquals(listOf(Problem.ContainersDown(listOf("x"))), b.problems(20_000))
    }

    @Test
    fun problems_sorted_by_severity() {
        val d = started("x" to "running")
        d.onSnapshot(snap("x" to "exited", cpu = 95.0, fs = listOf(FsInfo("/", 95.0))), 5_000)
        d.onSnapshot(snap("x" to "exited", cpu = 95.0, fs = listOf(FsInfo("/", 95.0))), 70_000)
        val p = d.problems(70_000)
        assertEquals(listOf(50, 40, 20), p.map { it.severity })
    }
}
