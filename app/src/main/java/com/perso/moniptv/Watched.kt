package com.perso.moniptv

import android.content.Context

/**
 * Titres « déjà vus » (#19), enregistrés sur le téléphone.
 *
 * Clés : `movie:<stableKey>` et `series:<stableKey>` (comme [Favorites.key]),
 * `ep:<série>:<épisode>` pour un épisode (comme « En cours » et les téléchargements).
 * Valeur : date à laquelle le titre a été marqué vu (ms).
 *
 * Un film ou un épisode est marqué automatiquement quand on le regarde jusqu'au bout
 * (voir [WatchProgress.record]) ; une série l'est quand tous ses épisodes sont vus.
 * Tout peut aussi être marqué / démarqué à la main. Conservé après « Changer de compte ».
 */
object Watched {
    private const val PREFS = "watched"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): Map<String, Long> =
        prefs(ctx).all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    fun mark(ctx: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        val e = p.edit()
        keys.forEach { if (!p.contains(it)) e.putLong(it, now) }
        e.apply()
    }

    fun unmark(ctx: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val e = prefs(ctx).edit()
        keys.forEach { e.remove(it) }
        e.apply()
    }

    /** Démarque tous les épisodes d'une série. */
    fun unmarkEpisodes(ctx: Context, seriesId: String) {
        val prefix = "ep:$seriesId:"
        unmark(ctx, prefs(ctx).all.keys.filter { it.startsWith(prefix) })
    }
}
