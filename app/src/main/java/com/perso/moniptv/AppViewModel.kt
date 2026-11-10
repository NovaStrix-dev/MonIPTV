package com.perso.moniptv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val store = AccountStore(ctx)
    val downloads = Downloads(ctx)

    var account by mutableStateOf<Account?>(null)
        private set
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** Message bref à afficher une fois (ex. résultat d'une actualisation). */
    var info by mutableStateOf<String?>(null)

    /** Films et épisodes commencés mais pas terminés (section « En cours »), du plus récent au plus ancien. */
    var watching by mutableStateOf(WatchProgress.all(ctx))
        private set

    /** À appeler au retour du lecteur : la position vient d'être enregistrée. */
    fun reloadWatching() {
        watching = WatchProgress.all(ctx)
        watched = Watched.all(ctx)
    }

    /** « Déjà vu » (#19) : clé (voir [Watched]) → date. */
    var watched by mutableStateOf(Watched.all(ctx))
        private set

    fun isWatched(m: Media): Boolean = Favorites.key(m) in watched

    fun isEpisodeWatched(key: String): Boolean = key in watched

    /**
     * Marque un film ou une série comme vu (ou non vu).
     * Pour une série, [episodeKeys] (s'ils sont connus) suivent le même état ; démarquer une série démarque
     * aussi tous ses épisodes. Un titre marqué vu sort de « En cours ».
     */
    fun setWatched(m: Media, value: Boolean, episodeKeys: List<String> = emptyList()) {
        val key = Favorites.key(m)
        if (value) {
            Watched.mark(ctx, listOf(key) + episodeKeys)
            if (m.type == ContentType.SERIES) WatchProgress.removeSeries(ctx, m.id) else WatchProgress.remove(ctx, key)
        } else {
            Watched.unmark(ctx, listOf(key))
            if (m.type == ContentType.SERIES) Watched.unmarkEpisodes(ctx, m.id)
        }
        reloadWatching()
    }

    /** Marque un épisode comme vu ou non vu. Démarquer un épisode démarque aussi la série. */
    fun setEpisodeWatched(series: Media, key: String, value: Boolean) {
        if (value) {
            Watched.mark(ctx, listOf(key))
            WatchProgress.all(ctx).firstOrNull { it.meta.key == key }?.let { WatchProgress.removeSeries(ctx, series.id) }
        } else {
            Watched.unmark(ctx, listOf(key, Favorites.key(series)))
        }
        reloadWatching()
    }

    /** Tous les épisodes vus : la série devient « vue » (sans toucher à « En cours »). */
    fun markSeriesWatchedIfComplete(series: Media, episodeKeys: List<String>) {
        val key = Favorites.key(series)
        if (episodeKeys.isEmpty() || key in watched || !episodeKeys.all { it in watched }) return
        Watched.mark(ctx, listOf(key))
        watched = Watched.all(ctx)
    }

    /** Favoris : clé (voir [Favorites.key]) → date d'ajout aux favoris. */
    var favorites by mutableStateOf(Favorites.all(ctx))
        private set

    fun isFavorite(m: Media): Boolean = Favorites.key(m) in favorites

    /** Ajoute ou retire un film / une série des favoris. Renvoie true s'il est maintenant en favori. */
    fun toggleFavorite(m: Media): Boolean {
        val now = !isFavorite(m)
        if (now) Favorites.add(ctx, m) else Favorites.remove(ctx, m)
        favorites = Favorites.all(ctx)
        return now
    }

    fun removeWatching(key: String) {
        WatchProgress.remove(ctx, key)
        reloadWatching()
    }

    /** Entrée « En cours » d'une série (dernier épisode regardé), ou null. */
    fun watchingSeries(seriesId: String): WatchEntry? = watching.firstOrNull { it.meta.seriesId == seriesId }

    fun removeWatchingSeries(seriesId: String) {
        WatchProgress.removeSeries(ctx, seriesId)
        reloadWatching()
    }

    /**
     * Lance un film ou un épisode en gardant sa progression pour « En cours ».
     * Si le titre a été téléchargé, c'est le fichier du téléphone qui est lu (marche hors ligne).
     */
    fun play(context: android.content.Context, meta: WatchMeta) {
        val local = downloads.records().firstOrNull { it.key == meta.key }
            ?.let { downloads.fileOf(it) }
            ?.takeIf { it.exists() && it.length() > 0 && !downloads.isRunning(meta.key) }
        val label = listOfNotNull(meta.title, meta.subtitle).joinToString(" · ")
        PlayerActivity.start(
            context,
            local?.let { android.net.Uri.fromFile(it).toString() } ?: meta.url,
            label,
            live = false,
            meta = meta,
        )
    }

    init {
        account = runCatching { store.load() }.getOrNull()
        if (account != null) {
            loading = true
            viewModelScope.launch {
                catalog = withContext(Dispatchers.IO) { CatalogCache.read(ctx) }
                loading = false
                if (catalog == null) refresh()
            }
        }
    }

    fun login(input: Account) {
        var server = input.server.trim()
        if (!server.startsWith("http://", true) && !server.startsWith("https://", true)) {
            server = "http://$server"
        }
        val acc = input.copy(server = server, username = input.username.trim())
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val c = Repository.fetchCatalog(acc)
                logCatalog(c)
                store.save(acc)
                withContext(Dispatchers.IO) { CatalogCache.write(ctx, c) }
                account = acc
                Epg.reset()
                catalog = c
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        val acc = account ?: return
        if (loading && catalog != null) return
        loading = true
        error = null
        val old = catalog
        viewModelScope.launch {
            try {
                val c = Repository.fetchCatalog(acc, old)
                logCatalog(c)
                withContext(Dispatchers.IO) { CatalogCache.write(ctx, c) }
                catalog = c
                val base = if (old == null) "Catalogue chargé" else {
                    val newMovies = countNew(old.movies, c.movies)
                    val newSeries = countNew(old.series, c.series)
                    if (newMovies + newSeries == 0) "Catalogue à jour, aucun nouveau titre"
                    else "Catalogue à jour : $newMovies nouveau(x) film(s), $newSeries nouvelle(s) série(s)"
                }
                val missing = Repository.lastProblems.map { it.substringBefore(" :") }
                info = if (missing.isEmpty()) base
                else "$base · non reçu : ${missing.joinToString(", ")} (détail dans le journal)"
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                loading = false
            }
        }
    }

    /** Note dans le journal de diagnostic ce que le serveur a envoyé (#29). */
    private fun logCatalog(c: Catalog) {
        val problems = Repository.lastProblems
        Diagnostics.log(
            ctx,
            "catalogue reçu : ${c.live.size} chaînes, ${c.movies.size} films, ${c.series.size} séries" +
                if (problems.isEmpty()) "" else " · échecs : ${problems.joinToString(" ; ")}",
        )
    }

    private fun countNew(old: List<Media>, fresh: List<Media>): Int {
        val known = old.mapTo(HashSet(old.size * 2)) { it.stableKey }
        return fresh.count { it.stableKey !in known }
    }

    fun logout() {
        store.clear()
        CatalogCache.delete(ctx)
        WatchProgress.clear(ctx)
        watching = emptyList()
        account = null
        Epg.reset()
        catalog = null
        error = null
    }

    suspend fun seriesInfo(series: Media): SeriesInfo {
        val acc = account ?: return SeriesInfo(null, emptyList())
        return Repository.seriesInfo(acc, catalog, series).also { info ->
            info.details?.let { detailsCache[Favorites.key(series)] = it }
        }
    }

    /** Fiches déjà chargées pendant cette session (évite de redemander au serveur). */
    private val detailsCache = HashMap<String, MediaDetails>()

    /** Résumé d'un film ; null si indisponible (M3U, hors ligne, serveur sans fiche). */
    suspend fun movieDetails(movie: Media): MediaDetails? {
        detailsCache[Favorites.key(movie)]?.let { return it }
        val acc = account ?: return null
        val d = runCatching { Repository.movieDetails(acc, movie) }.getOrNull() ?: return null
        detailsCache[Favorites.key(movie)] = d
        return d
    }

    /** L'utilisateur a choisi de faire passer MonIPTV à côté du VPN. */
    var bypassVpn by mutableStateOf(SystemNetwork.bypassVpn)
        private set

    fun vpnActive(): Boolean = Http.isVpnActive(ctx)

    fun changeBypassVpn(on: Boolean) {
        SystemNetwork.bypassVpn = on
        bypassVpn = on
    }

    private fun friendly(e: Exception): String = friendlyBase(e) + "\n\nDétail : " + SystemNetwork.describe(e)

    private fun friendlyBase(e: Exception): String {
        val base = when (e) {
            is UnknownHostException -> "Serveur introuvable (vérifiez l'adresse ou la connexion Internet)"
            is ConnectException -> "Impossible de joindre le serveur"
            is SocketTimeoutException -> "Le serveur met trop de temps à répondre"
            is SSLException -> "Connexion sécurisée impossible avec le serveur"
            is HttpStatusException -> when (e.code) {
                401, 403, 451 -> "Le serveur refuse l'accès (HTTP ${e.code})"
                404 -> "Adresse introuvable sur le serveur (HTTP 404) : vérifiez l'adresse"
                429 -> "Trop de connexions en même temps (HTTP 429), réessayez dans un moment"
                in 500..599 -> "Le serveur rencontre un problème (HTTP ${e.code}), réessayez plus tard"
                else -> e.message ?: "Erreur du serveur"
            }
            else -> e.message ?: "Erreur inconnue"
        }
        if (!Http.isVpnActive(ctx)) return base
        return base + "\n\nUn VPN est actif. Si tout marche sans lui, le fournisseur IPTV bloque " +
            "probablement les adresses de ce VPN : essayez un autre pays ou serveur dans le VPN, " +
            "ou excluez MonIPTV du VPN (option « tunnel fractionné » ou « split tunneling » de l'appli VPN), " +
            "ou touchez « Contourner le VPN pour MonIPTV »."
    }
}
