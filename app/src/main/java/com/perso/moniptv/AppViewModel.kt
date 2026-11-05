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
    }

    fun removeWatching(key: String) {
        WatchProgress.remove(ctx, key)
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
                store.save(acc)
                withContext(Dispatchers.IO) { CatalogCache.write(ctx, c) }
                account = acc
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
                withContext(Dispatchers.IO) { CatalogCache.write(ctx, c) }
                catalog = c
                info = if (old == null) "Catalogue chargé" else {
                    val newMovies = countNew(old.movies, c.movies)
                    val newSeries = countNew(old.series, c.series)
                    if (newMovies + newSeries == 0) "Catalogue à jour, aucun nouveau titre"
                    else "Catalogue à jour : $newMovies nouveau(x) film(s), $newSeries nouvelle(s) série(s)"
                }
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                loading = false
            }
        }
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
        catalog = null
        error = null
    }

    suspend fun episodes(series: Media): List<Episode> {
        val acc = account ?: return emptyList()
        return Repository.episodes(acc, catalog, series)
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "Serveur introuvable (vérifiez l'adresse ou la connexion Internet)"
        is ConnectException -> "Impossible de joindre le serveur"
        is SocketTimeoutException -> "Le serveur met trop de temps à répondre"
        else -> e.message ?: "Erreur inconnue"
    }
}
