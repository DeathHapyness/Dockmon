package dev.rique.guardiao.music

import org.json.JSONException
import org.json.JSONObject

data class NowPlaying(val title: String, val artist: String, val coverUrl: String?, val isPlaying: Boolean)

sealed class PlayResult {
    data class Playing(val np: NowPlaying) : PlayResult()
    data object Nothing : PlayResult()
    data object NoDevice : PlayResult()
    data object AuthError : PlayResult()
    data object NetError : PlayResult()
}

object SpotifyParser {
    fun nowPlaying(code: Int, body: String?): PlayResult = when {
        code == 204 -> PlayResult.Nothing
        code == 401 -> PlayResult.AuthError
        code != 200 || body == null -> PlayResult.NetError
        else -> try {
            val o = JSONObject(body)
            val item = o.optJSONObject("item")
            if (item == null) PlayResult.Nothing
            else {
                val artists = item.optJSONArray("artists")
                val names = (0 until (artists?.length() ?: 0)).map { artists!!.getJSONObject(it).getString("name") }
                PlayResult.Playing(
                    NowPlaying(item.getString("name"), names.joinToString(", "), cover(item), o.optBoolean("is_playing")),
                )
            }
        } catch (e: JSONException) {
            PlayResult.NetError
        }
    }

    fun control(code: Int): PlayResult? = when {
        code in 200..299 -> null
        code == 404 -> PlayResult.NoDevice
        code == 401 -> PlayResult.AuthError
        else -> PlayResult.NetError
    }

    private fun cover(item: JSONObject): String? {
        val images = item.optJSONObject("album")?.optJSONArray("images") ?: return null
        var best: String? = null
        var bestDiff = Int.MAX_VALUE
        for (i in 0 until images.length()) {
            val img = images.getJSONObject(i)
            val diff = kotlin.math.abs(img.optInt("width", 0) - 300)
            if (diff < bestDiff) {
                bestDiff = diff
                best = img.getString("url")
            }
        }
        return best
    }
}
