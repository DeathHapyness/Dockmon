package dev.rique.guardiao.server

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GlancesClient(private val baseUrl: String) {
    fun fetch(): ServerSnapshot? {
        val containers = get("containers")?.let(GlancesParser::containers) ?: return null
        val cpu = get("cpu/total")?.let(GlancesParser::cpu) ?: return null
        val fs = get("fs")?.let(GlancesParser::fs).orEmpty()
        val sensors = get("sensors")?.let(GlancesParser::sensors).orEmpty()
        val mem = get("mem")?.let(GlancesParser::mem)
        val uptime = get("uptime")?.let(GlancesParser::uptime)
        return ServerSnapshot(containers, cpu, fs, sensors, mem, uptime)
    }

    private fun get(path: String): String? {
        val conn = try {
            URL("${baseUrl.trimEnd('/')}/api/4/$path").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return null
        }
        return try {
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
