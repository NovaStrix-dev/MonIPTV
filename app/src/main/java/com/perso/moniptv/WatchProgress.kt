package com.perso.moniptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ce qu'il faut savoir pour reprendre un film ou un épisode depuis la section « En cours ».
 *
 * [key] est la même clé que celle des téléchargements (« movie:… » ou « ep:<série>:<épisode> ») :
 * la progression est donc partagée entre la lecture en ligne et le fichier téléchargé.
 */
data class WatchMeta(
    val key: String,
    val type: ContentType,
    val title: String,
    /** Pour un épisode : « S01E03 · Titre de l'épisode ». */
    val subtitle: String?,
    val icon: String?,
    /** Adresse de lecture en ligne. */
    val url: String,
    /** Pour un épisode : identifiant de la série (pour l'ouvrir depuis « En cours »). */
    val seriesId: String? = null,
)

data class WatchEntry(
    val meta: WatchMeta,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val remainingMin: Long get() = if (duration > 0) ((duration - position) / 60_000).coerceAtLeast(1) else 0
}

/** Position de lecture et liste « En cours », enregistrées sur le téléphone. */
object WatchProgress {
    private const val PREFS = "watching"
    private const val LIST = "list"

    /** En dessous, on considère que le titre a juste été ouvert (pas « en cours »). */
    private const val MIN_WATCHED_MS = 20_000L
    /** Dans la dernière minute (ou au-delà de 95 %), le titre est considéré comme terminé. */
    private const val END_MARGIN_MS = 60_000L
    private const val MAX_ENTRIES = 40

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(ctx: Context): List<WatchEntry> {
        val raw = prefs(ctx).getString(LIST, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                WatchEntry(
                    WatchMeta(
                        key = o.getString("key"),
                        type = runCatching { ContentType.valueOf(o.getString("type")) }.getOrDefault(ContentType.MOVIE),
                        title = o.getString("title"),
                        subtitle = o.optString("subtitle").ifEmpty { null },
                        icon = o.optString("icon").ifEmpty { null },
                        url = o.getString("url"),
                        seriesId = o.optString("seriesId").ifEmpty { null },
                    ),
                    position = o.getLong("pos"),
                    duration = o.optLong("dur"),
                    updatedAt = o.optLong("at"),
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun save(ctx: Context, list: List<WatchEntry>) {
        val a = JSONArray()
        list.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES).forEach { e ->
            val m = e.meta
            a.put(
                JSONObject()
                    .put("key", m.key).put("type", m.type.name).put("title", m.title)
                    .put("subtitle", m.subtitle ?: "").put("icon", m.icon ?: "").put("url", m.url)
                    .put("seriesId", m.seriesId ?: "")
                    .put("pos", e.position).put("dur", e.duration).put("at", e.updatedAt)
            )
        }
        prefs(ctx).edit().putString(LIST, a.toString()).apply()
    }

    fun position(ctx: Context, key: String): Long = all(ctx).firstOrNull { it.meta.key == key }?.position ?: 0L

    fun entry(ctx: Context, key: String): WatchEntry? = all(ctx).firstOrNull { it.meta.key == key }

    /** Enregistre la position à la fermeture du lecteur ; retire le titre s'il est terminé. */
    @Synchronized
    fun record(ctx: Context, meta: WatchMeta, position: Long, duration: Long) {
        val others = all(ctx).filter { it.meta.key != meta.key }
        val finished = duration > 0 && (position > duration - END_MARGIN_MS || position > duration * 0.95)
        if (finished || position < MIN_WATCHED_MS) {
            // Terminé : on le retire. Juste ouvert : on garde l'ancienne position si elle existait.
            val old = all(ctx).firstOrNull { it.meta.key == meta.key }
            save(ctx, if (finished || old == null) others else others + old)
            return
        }
        // Pour une série, un seul épisode « en cours » à la fois : le dernier regardé.
        val kept = if (meta.seriesId != null) others.filter { it.meta.seriesId != meta.seriesId } else others
        save(ctx, kept + WatchEntry(meta, position, duration, System.currentTimeMillis()))
    }

    /**
     * Retire un titre de « En cours ». Pour un épisode, toute la série est retirée
     * (y compris d'éventuelles anciennes entrées d'autres épisodes de la même série).
     */
    @Synchronized
    fun remove(ctx: Context, key: String) {
        val list = all(ctx)
        val seriesId = list.firstOrNull { it.meta.key == key }?.meta?.seriesId
            ?: key.takeIf { it.startsWith("ep:") }?.split(':')?.getOrNull(1)
        save(ctx, list.filter { it.meta.key != key && (seriesId == null || it.meta.seriesId != seriesId) })
    }

    /** Retire de « En cours » tous les épisodes d'une série. */
    @Synchronized
    fun removeSeries(ctx: Context, seriesId: String) {
        save(ctx, all(ctx).filter { it.meta.seriesId != seriesId })
    }

    @Synchronized
    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(LIST).apply()
    }
}

/** Métadonnées « En cours » d'un film du catalogue. */
fun Media.watchMeta(): WatchMeta? = url?.let {
    WatchMeta(key = "movie:$stableKey", type = ContentType.MOVIE, title = name, subtitle = null, icon = icon, url = it)
}

/** Métadonnées « En cours » d'un épisode. */
fun Episode.watchMeta(series: Media): WatchMeta = WatchMeta(
    key = "ep:${series.id}:$id",
    type = ContentType.SERIES,
    title = series.name,
    subtitle = "S%02dE%02d".format(season, number) + if (title.isNotBlank()) " · $title" else "",
    icon = series.icon,
    url = url,
    seriesId = series.id,
)
