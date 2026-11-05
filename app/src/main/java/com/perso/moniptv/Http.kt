package com.perso.moniptv

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    /** Beaucoup de serveurs IPTV refusent les clients inconnus : on se présente comme VLC. */
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .build()

    fun get(url: String): String {
        client.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Erreur du serveur (HTTP ${r.code})")
            return r.body?.string() ?: ""
        }
    }

    /** Lit une réponse ligne par ligne sans tout charger en mémoire (grosses playlists M3U). */
    fun <T> getLines(url: String, block: (Sequence<String>) -> T): T {
        client.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Erreur du serveur (HTTP ${r.code})")
            val body = r.body ?: throw IOException("Réponse vide")
            return body.charStream().buffered().useLines(block)
        }
    }
}
