package com.perso.moniptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

object Repository {

    /**
     * Télécharge le catalogue. [previous] (ancien catalogue) sert à dater les titres
     * que le serveur ne date pas : un titre absent la fois précédente est daté de maintenant,
     * ce qui le fait apparaître dans « Nouveautés ».
     */
    suspend fun fetchCatalog(acc: Account, previous: Catalog? = null): Catalog = withContext(Dispatchers.IO) {
        val c = when (acc.type) {
            AccountType.XTREAM -> fetchXtream(acc, previous)
            AccountType.M3U -> {
                val parsed = Http.getLines(m3uUrl(acc)) { M3uParser.parse(it) }
                if (parsed.live.isEmpty() && parsed.movies.isEmpty() && parsed.series.isEmpty()) {
                    throw IOException("Playlist vide ou invalide")
                }
                parsed
            }
        }
        val now = System.currentTimeMillis()
        c.copy(
            movies = stamp(c.movies, previous?.movies, now / 1000),
            series = stamp(c.series, previous?.series, now / 1000),
            updatedAt = now,
        )
    }

    private fun stamp(fresh: List<Media>, old: List<Media>?, nowSec: Long): List<Media> {
        if (old == null) return fresh
        val known = HashMap<String, Long>(old.size * 2)
        old.forEach { known[it.stableKey] = it.added }
        return fresh.map { m ->
            if (m.added > 0) m else m.copy(added = known[m.stableKey] ?: nowSec)
        }
    }

    /**
     * Les listes sont chargées en parallèle. Si l'une échoue (connexion coupée, délai dépassé, fréquent
     * derrière un VPN), on garde la version de l'actualisation précédente au lieu d'enregistrer
     * une liste vide (ce qui vidait la bibliothèque). Sans version précédente, l'erreur est remontée.
     */
    private suspend fun fetchXtream(acc: Account, previous: Catalog?): Catalog = coroutineScope {
        val api = XtreamApi(acc)
        val ext = api.authenticate()
        val liveCats = async { runCatching { api.categories("get_live_categories") } }
        val live = async { runCatching { api.live(ext) } }
        val movieCats = async { runCatching { api.categories("get_vod_categories") } }
        val movies = async { runCatching { api.movies() } }
        val seriesCats = async { runCatching { api.categories("get_series_categories") } }
        val series = async { runCatching { api.series() } }

        fun <T> cats(r: Result<List<T>>, old: List<T>?): List<T> =
            r.getOrNull() ?: old ?: emptyList()

        // Coupure réseau sans version précédente : on remonte l'erreur (sinon la liste resterait vide).
        // Réponse refusée ou illisible pour une seule liste (serveur sans séries, par ex.) : liste vide.
        fun <T> items(r: Result<List<T>>, old: List<T>?): List<T> = r.getOrElse { e ->
            when {
                !old.isNullOrEmpty() -> old
                e is IOException && e !is HttpStatusException -> throw e
                else -> emptyList()
            }
        }

        val c = Catalog(
            cats(liveCats.await(), previous?.liveCats), items(live.await(), previous?.live),
            cats(movieCats.await(), previous?.movieCats), items(movies.await(), previous?.movies),
            cats(seriesCats.await(), previous?.seriesCats), items(series.await(), previous?.series),
        )
        if (c.live.isEmpty() && c.movies.isEmpty() && c.series.isEmpty()) {
            throw IOException("Connexion réussie mais aucun contenu reçu du serveur")
        }
        c
    }

    /**
     * En mode M3U : soit une URL complète de playlist, soit une adresse de serveur
     * + identifiant / mot de passe (on construit alors le lien get.php standard).
     */
    fun m3uUrl(acc: Account): String {
        val s = acc.server.trim()
        val isFullUrl = "get.php" in s || ".m3u" in s.lowercase() || '?' in s
        if (isFullUrl || acc.username.isBlank()) return s
        val u = URLEncoder.encode(acc.username, "UTF-8")
        val p = URLEncoder.encode(acc.password, "UTF-8")
        return "${s.trimEnd('/')}/get.php?username=$u&password=$p&type=m3u_plus&output=ts"
    }

    suspend fun episodes(acc: Account, catalog: Catalog?, series: Media): List<Episode> =
        withContext(Dispatchers.IO) {
            when (acc.type) {
                AccountType.XTREAM -> XtreamApi(acc).episodes(series.id)
                AccountType.M3U -> catalog?.m3uEpisodes?.get(series.id) ?: emptyList()
            }
        }
}
