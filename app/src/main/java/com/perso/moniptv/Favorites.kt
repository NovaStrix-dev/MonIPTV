package com.perso.moniptv

import android.content.Context

/**
 * Films et séries marqués « favoris », enregistrés sur le téléphone.
 * Clé = `movie:<stableKey>` ou `series:<stableKey>` ; valeur = date d'ajout aux favoris (ms),
 * pour afficher les derniers ajoutés en premier. Conservés après une actualisation du catalogue
 * et après « Changer de compte » (ils ne s'affichent que si le titre existe dans le catalogue).
 */
object Favorites {
    private const val PREFS = "favorites"

    fun key(m: Media): String = when (m.type) {
        ContentType.SERIES -> "series:${m.stableKey}"
        ContentType.MOVIE -> "movie:${m.stableKey}"
        ContentType.LIVE -> "live:${m.stableKey}"
    }

    fun all(ctx: Context): Map<String, Long> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }
            .toMap()

    fun add(ctx: Context, m: Media) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(key(m), System.currentTimeMillis()).apply()
    }

    fun remove(ctx: Context, m: Media) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(m)).apply()
    }
}
