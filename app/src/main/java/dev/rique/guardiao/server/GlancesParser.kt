package dev.rique.guardiao.server

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GlancesParser {
    fun containers(json: String): List<ContainerInfo>? = array(json) { o ->
        ContainerInfo(o.getString("name"), o.optString("status", ""))
    }

    fun cpu(json: String): Double? = try {
        JSONObject(json).getDouble("total")
    } catch (e: JSONException) {
        null
    }

    fun fs(json: String): List<FsInfo>? = array(json) { o ->
        FsInfo(o.getString("mnt_point"), o.getDouble("percent"))
    }

    fun sensors(json: String): List<SensorInfo>? = array(json) { o ->
        if (!o.optString("type").startsWith("temperature") || o.optString("unit") != "C") null
        else SensorInfo(o.getString("label"), o.getDouble("value"), o.optNumber("warning"), o.optNumber("critical"))
    }

    private fun JSONObject.optNumber(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun <T> array(json: String, map: (JSONObject) -> T?): List<T>? = try {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { map(a.getJSONObject(it)) }
    } catch (e: JSONException) {
        null
    }
}
