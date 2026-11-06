package dev.rique.guardiao.music

import dev.rique.guardiao.settings.Settings
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SpotifyApi(private val settings: Settings) {
    private var accessToken: String? = null

    fun current(): PlayResult {
        val r = call("GET", "me/player/currently-playing") ?: return PlayResult.NetError
        return SpotifyParser.nowPlaying(r.first, r.second)
    }

    fun play() = control("PUT", "me/player/play")
    fun pause() = control("PUT", "me/player/pause")
    fun next() = control("POST", "me/player/next")
    fun previous() = control("POST", "me/player/previous")

    private fun control(method: String, path: String): PlayResult? {
        val r = call(method, path) ?: return PlayResult.NetError
        return SpotifyParser.control(r.first)
    }

    private fun call(method: String, path: String): Pair<Int, String?>? {
        if (accessToken == null && !refresh()) return 401 to null
        var r = request(method, path) ?: return null
        if (r.first == 401 && refresh()) r = request(method, path) ?: return null
        return r
    }

    private fun request(method: String, path: String): Pair<Int, String?>? = http(method, "https://api.spotify.com/v1/$path") {
        setRequestProperty("Authorization", "Bearer $accessToken")
        if (method != "GET") {
            doOutput = true
            setFixedLengthStreamingMode(0)
        }
    }

    private fun refresh(): Boolean {
        val token = settings.spotifyRefreshToken ?: return false
        val clientId = settings.spotifyClientId ?: return false
        val form = "grant_type=refresh_token&refresh_token=${Pkce.enc(token)}&client_id=${Pkce.enc(clientId)}"
        val r = http("POST", "https://accounts.spotify.com/api/token") {
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(form.toByteArray()) }
        } ?: return false
        if (r.first != 200 || r.second == null) return false
        return try {
            val o = JSONObject(r.second!!)
            accessToken = o.getString("access_token")
            o.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { settings.spotifyRefreshToken = it }
            true
        } catch (e: JSONException) {
            false
        }
    }

    private fun http(method: String, url: String, setup: HttpURLConnection.() -> Unit): Pair<Int, String?>? {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return null
        }
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setup()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to stream?.bufferedReader()?.use { it.readText() }?.takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
