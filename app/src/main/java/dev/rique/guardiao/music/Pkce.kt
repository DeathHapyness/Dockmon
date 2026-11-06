package dev.rique.guardiao.music

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    const val SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state"

    fun verifier(random: SecureRandom): String =
        String(CharArray(64) { ALPHABET[random.nextInt(ALPHABET.length)] })

    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun authUrl(clientId: String, challenge: String, redirectUri: String): String =
        "https://accounts.spotify.com/authorize?response_type=code" +
            "&client_id=${enc(clientId)}" +
            "&redirect_uri=${enc(redirectUri)}" +
            "&code_challenge_method=S256" +
            "&code_challenge=${enc(challenge)}" +
            "&scope=${enc(SCOPES)}"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // java.util.Base64 needs API 26 and android.util.Base64 is unavailable in JVM tests.
    private fun base64Url(bytes: ByteArray): String {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            out.append(B64[b0 shr 2])
            out.append(B64[((b0 and 0x03) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
            if (b1 >= 0) out.append(B64[((b1 and 0x0F) shl 2) or (if (b2 < 0) 0 else b2 shr 6)])
            if (b2 >= 0) out.append(B64[b2 and 0x3F])
            i += 3
        }
        return out.toString()
    }
}
