package dev.rique.guardiao.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlancesParserTest {
    @Test
    fun parse_containers() {
        val r = GlancesParser.containers("""[{"name":"jellyfin","status":"healthy"},{"name":"n8n","status":"running"}]""")!!
        assertEquals(2, r.size)
        assertEquals(ContainerInfo("jellyfin", "healthy"), r[0])
    }

    @Test
    fun parse_cpu() {
        assertEquals(18.3, GlancesParser.cpu("""{"total": 18.3}""")!!, 1e-9)
    }

    @Test
    fun parse_fs() {
        val r = GlancesParser.fs("""[{"mnt_point":"/etc/hosts","fs_type":"ext4","percent":17.6}]""")!!
        assertEquals(listOf(FsInfo("/etc/hosts", 17.6)), r)
    }

    @Test
    fun parse_sensors_only_temperature() {
        val json = """[{"label":"Core 0","type":"temperature_core","value":32,"unit":"C","warning":74,"critical":94},
            {"label":"acpitz 0","type":"temperature_core","value":29,"unit":"C","warning":null,"critical":null},
            {"label":"fan1","type":"fan_speed","value":1200,"unit":"R","warning":null,"critical":null}]"""
        val r = GlancesParser.sensors(json)!!
        assertEquals(2, r.size)
        assertEquals(SensorInfo("Core 0", 32.0, 74.0, 94.0), r[0])
        assertNull(r[1].warning)
    }

    @Test
    fun parse_invalid_json_returns_null() {
        assertNull(GlancesParser.containers("<html>"))
        assertNull(GlancesParser.cpu("<html>"))
    }
}
