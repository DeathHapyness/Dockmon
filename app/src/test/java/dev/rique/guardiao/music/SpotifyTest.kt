package dev.rique.guardiao.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class SpotifyTest {
    @Test
    fun challenge_known_vector() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun verifier_length_and_alphabet() {
        val v = Pkce.verifier(SecureRandom())
        assertEquals(64, v.length)
        assertTrue(v.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun auth_url_contains_scopes_and_redirect() {
        val url = Pkce.authUrl("abc", "xyz", "guardiao://callback")
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("client_id=abc"))
        assertTrue(url.contains("code_challenge=xyz"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("redirect_uri=guardiao%3A%2F%2Fcallback"))
        assertTrue(url.contains("scope=user-read-currently-playing%20user-read-playback-state%20user-modify-playback-state"))
    }

    @Test
    fun parse_playing() {
        val body = """{"is_playing":true,"item":{"name":"Song","artists":[{"name":"A"},{"name":"B"}],
            "album":{"images":[{"url":"big","width":640},{"url":"mid","width":300},{"url":"small","width":64}]}}}"""
        assertEquals(PlayResult.Playing(NowPlaying("Song", "A, B", "mid", true)), SpotifyParser.nowPlaying(200, body))
    }

    @Test
    fun parse_204_is_nothing_playing() {
        assertEquals(PlayResult.Nothing, SpotifyParser.nowPlaying(204, null))
        assertEquals(PlayResult.Nothing, SpotifyParser.nowPlaying(200, """{"is_playing":false,"item":null}"""))
    }

    @Test
    fun parse_401_is_auth_error() {
        assertEquals(PlayResult.AuthError, SpotifyParser.nowPlaying(401, "{}"))
    }

    @Test
    fun control_404_maps_to_no_device() {
        assertNull(SpotifyParser.control(204))
        assertEquals(PlayResult.NoDevice, SpotifyParser.control(404))
        assertEquals(PlayResult.AuthError, SpotifyParser.control(401))
    }
}
