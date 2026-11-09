package com.perso.moniptv

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Un programme TV (heures en millisecondes depuis 1970). */
data class Program(val title: String, val start: Long, val end: Long, val description: String?) {
    /** « 20:45 – 22:30 » */
    val hours: String get() = "${hhmm(start)} – ${hhmm(end)}"

    /** Avancement du programme (0 à 1) à l'instant [now]. */
    fun progress(now: Long): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    companion object {
        fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))
    }
}

/**
 * Guide des programmes (#31) : programme en cours et suivant de chaque chaîne, via l'API Xtream `get_short_epg`.
 * Marche aussi avec une playlist M3U d'un serveur Xtream (lien get.php avec identifiant et mot de passe).
 * Chargé à la demande (seulement les chaînes affichées), gardé en mémoire jusqu'à la fin du programme en cours.
 */
object Epg {
    private class Entry(val programs: List<Program>, val fetchedAt: Long)

    /** Serveur Xtream à interroger, ou null si le compte n'en a pas (playlist M3U simple). */
    private class Source(val api: XtreamApi, val fromM3u: Boolean)

    private val cache = ConcurrentHashMap<String, Entry>()
    /** Peu de requêtes en même temps : le défilement de la liste ne doit pas inonder le serveur. */
    private val gate = Semaphore(3)
    @Volatile private var source: Source? = null
    @Volatile private var sourceLoaded = false

    private const val LIMIT = 4
    private const val MAX_AGE_MS = 30 * 60_000L
    private const val RETRY_AFTER_FAILURE_MS = 10 * 60_000L

    /** Nouveau compte (connexion, changement de compte) : on oublie tout. */
    fun reset() {
        cache.clear()
        source = null
        sourceLoaded = false
    }

    private fun source(ctx: Context): Source? {
        if (sourceLoaded) return source
        synchronized(this) {
            if (!sourceLoaded) {
                source = runCatching { AccountStore(ctx.applicationContext).load() }.getOrNull()?.let { sourceFor(it) }
                sourceLoaded = true
            }
        }
        return source
    }

    private fun sourceFor(acc: Account): Source? = when (acc.type) {
        AccountType.XTREAM -> Source(XtreamApi(acc), fromM3u = false)
        AccountType.M3U -> m3uAsXtream(acc)?.let { Source(XtreamApi(it), fromM3u = true) }
    }

    /** Lien M3U « …/get.php?username=…&password=… » (ou serveur + identifiants) → compte Xtream équivalent. */
    private fun m3uAsXtream(acc: Account): Account? {
        val s = acc.server.trim()
        if ("get.php" in s) {
            val base = s.substringBefore("/get.php")
            val query = s.substringAfter('?', "")
            val params = query.split('&').mapNotNull { kv ->
                val i = kv.indexOf('=')
                if (i <= 0) null else kv.substring(0, i) to runCatching { URLDecoder.decode(kv.substring(i + 1), "UTF-8") }.getOrDefault("")
            }.toMap()
            val u = params["username"]?.takeIf { it.isNotBlank() } ?: return null
            val p = params["password"] ?: return null
            return Account(AccountType.XTREAM, base, u, p)
        }
        val isFullUrl = ".m3u" in s.lowercase() || '?' in s
        if (isFullUrl || acc.username.isBlank()) return null
        return Account(AccountType.XTREAM, s, acc.username, acc.password)
    }

    /** Numéro de la chaîne sur le serveur : id Xtream, ou dernier nombre du lien (…/live/u/p/1234.ts) en M3U. */
    private fun streamId(src: Source, id: String, url: String?): String? {
        if (!src.fromM3u) return id
        val path = runCatching { URI(url ?: return null).path }.getOrNull() ?: url?.substringBefore('?') ?: return null
        return Regex("(\\d+)(\\.[A-Za-z0-9]+)?$").find(path)?.groupValues?.get(1)
    }

    /** true si le compte permet d'avoir le guide des programmes. */
    fun available(ctx: Context): Boolean = source(ctx) != null

    /** Programmes déjà en mémoire et encore valables (sans requête), ou null. */
    fun cached(m: Media): List<Program>? = cached(m.id)

    fun cached(id: String): List<Program>? {
        val e = cache[id] ?: return null
        val now = System.currentTimeMillis()
        val left = e.programs.filter { it.end > now }
        val stale = now - e.fetchedAt > MAX_AGE_MS || (e.programs.isNotEmpty() && left.isEmpty())
        return if (stale) null else left
    }

    suspend fun programs(ctx: Context, m: Media): List<Program> = programs(ctx, m.id, m.url)

    /** Programme en cours puis suivants ; liste vide si le serveur n'en donne pas. */
    suspend fun programs(ctx: Context, id: String, url: String?): List<Program> {
        cached(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val src = source(ctx) ?: return@withContext emptyList()
            val sid = streamId(src, id, url) ?: return@withContext emptyList()
            gate.withPermit {
                cached(id)?.let { return@withPermit it }
                val now = System.currentTimeMillis()
                val raw = runCatching { src.api.shortEpg(sid, LIMIT) }.getOrNull()
                if (raw == null) {
                    // Échec réseau : on réessaiera plus tard sans bloquer la liste.
                    cache[id] = Entry(emptyList(), now - MAX_AGE_MS + RETRY_AFTER_FAILURE_MS)
                    return@withPermit emptyList()
                }
                val list = raw.mapNotNull { parse(it) }.sortedBy { it.start }.filter { it.end > now }
                cache[id] = Entry(list, now)
                list
            }
        }
    }

    private fun parse(o: org.json.JSONObject): Program? {
        val title = decode(o.str("title")) ?: return null
        val start = o.str("start_timestamp")?.toLongOrNull()?.times(1000) ?: return null
        val end = (o.str("stop_timestamp") ?: o.str("end_timestamp"))?.toLongOrNull()?.times(1000) ?: return null
        return Program(title, start, end, decode(o.str("description")))
    }

    private val base64Re = Regex("^[A-Za-z0-9+/]+={0,2}$")

    /** Les serveurs Xtream envoient les textes en base64 ; certains en clair. */
    private fun decode(s: String?): String? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (t.length % 4 == 0 && base64Re.matches(t)) {
            val d = runCatching { String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
            if (d != null && '�' !in d && d.none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
                return d.trim().takeIf { it.isNotEmpty() }
            }
        }
        return t
    }
}
