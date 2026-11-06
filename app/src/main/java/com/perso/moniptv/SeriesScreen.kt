package com.perso.moniptv

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(vm: AppViewModel, series: Media, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var details by remember { mutableStateOf<MediaDetails?>(null) }
    var plotOpen by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    // Épisodes téléchargés, en cours ou en file d'attente.
    var dlKeys by remember {
        mutableStateOf(vm.downloads.records().map { it.key }.toSet() + vm.downloads.queue().map { it.key })
    }
    var confirmSeason by remember { mutableStateOf<Int?>(null) }
    var confirmRemoveWatching by remember { mutableStateOf(false) }
    val inProgress = vm.watchingSeries(series.id)

    fun episodeKey(e: Episode) = "ep:${series.id}:${e.id}"
    fun episodeLabel(e: Episode) = "S%02dE%02d".format(e.season, e.number)
    fun queued(e: Episode) = QueuedDownload(
        episodeKey(e), "${series.name} - ${episodeLabel(e)} - ${e.title}", series.icon, e.url, e.ext,
    )

    LaunchedEffect(series.id) {
        try {
            val info = vm.seriesInfo(series)
            details = info.details
            episodes = info.episodes
        } catch (e: Exception) {
            err = e.message ?: "Erreur de chargement"
        }
    }

    // Tous les épisodes vus : la série passe en « Vu » (#19).
    LaunchedEffect(episodes, vm.watched) {
        episodes?.let { eps -> vm.markSeriesWatchedIfComplete(series, eps.map { episodeKey(it) }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    WatchedButton(vm.isWatched(series)) {
                        toggleWatchedWithToast(ctx, vm, series, episodes.orEmpty().map { episodeKey(it) })
                    }
                    FavoriteButton(vm.isFavorite(series)) { toggleFavoriteWithToast(ctx, vm, series) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (inProgress != null) {
                // Série dans « En cours » : rappel de l'épisode et bouton pour l'en retirer (#18).
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "En cours : " + listOfNotNull(
                            inProgress.meta.subtitle?.substringBefore(" · "),
                            "arrêté à ${formatTime(inProgress.position)}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirmRemoveWatching = true }) { Text("Retirer de « En cours »") }
                }
            }
            val eps = episodes
            when {
                err != null -> CenterMessage(err ?: "")
                eps == null -> CenterMessage("Chargement des épisodes…", loading = true)
                eps.isEmpty() -> CenterMessage("Aucun épisode disponible")
                else -> {
                    val seasons = eps.map { it.season }.distinct().sorted()
                    val current = season ?: seasons.first()
                    Row(Modifier.padding(12.dp)) {
                        Poster(series.icon, series.name, Modifier.width(90.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(series.name, style = MaterialTheme.typography.titleMedium)
                            Text("${seasons.size} saison(s) · ${eps.size} épisode(s)", style = MaterialTheme.typography.bodySmall)
                            details?.facts?.takeIf { it.isNotEmpty() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            // Synopsis : 4 lignes, toucher pour tout lire.
                            details?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                                Text(
                                    plot,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = if (plotOpen) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp).clickable { plotOpen = !plotOpen },
                                )
                            }
                        }
                    }
                    if (seasons.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasons) { s ->
                                FilterChip(selected = s == current, onClick = { season = s }, label = { Text("Saison $s") })
                            }
                        }
                    }
                    // #16 : télécharger toute la saison affichée (épisodes mis en file, un à la fois).
                    val seasonEps = eps.filter { it.season == current }
                    val missing = seasonEps.count { episodeKey(it) !in dlKeys }
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { confirmSeason = current },
                            enabled = missing > 0,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                if (missing > 0) Icons.Filled.Download else Icons.Filled.DownloadDone,
                                null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (missing > 0) "Télécharger la saison $current" else "Saison $current téléchargée",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(seasonEps) { e ->
                            val key = episodeKey(e)
                            val label = episodeLabel(e)
                            val meta = e.watchMeta(series)
                            val started = vm.watching.firstOrNull { it.meta.key == meta.key }
                            val seen = vm.isEpisodeWatched(key)
                            ListItem(
                                headlineContent = { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Column {
                                        when {
                                            started != null -> Text("$label · en cours, arrêté à ${formatTime(started.position)}")
                                            seen -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("$label · ")
                                                Icon(Icons.Filled.Check, null, tint = WATCHED_BLUE, modifier = Modifier.size(14.dp))
                                                Text(" Vu", color = WATCHED_BLUE)
                                            }
                                            else -> Text(label)
                                        }
                                        if (started != null && started.duration > 0) {
                                            LinearProgressIndicator(
                                                progress = { started.progress },
                                                modifier = Modifier.padding(top = 4.dp).width(120.dp),
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { vm.setEpisodeWatched(series, key, !seen) }) {
                                            Icon(
                                                if (seen) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                                if (seen) "Marquer comme non vu" else "Marquer comme vu",
                                                tint = if (seen) WATCHED_BLUE else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            )
                                        }
                                        IconButton(onClick = {
                                            if (key in dlKeys) {
                                                Toast.makeText(ctx, "Déjà dans « Hors ligne »", Toast.LENGTH_SHORT).show()
                                            } else {
                                                // Passe par la file : ne démarre qu'une fois les téléchargements en cours finis.
                                                vm.downloads.enqueue(listOf(queued(e)))
                                                dlKeys = dlKeys + key
                                                Toast.makeText(ctx, "Ajouté à « Hors ligne »", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(if (key in dlKeys) Icons.Filled.DownloadDone else Icons.Filled.Download, "Télécharger")
                                        }
                                        IconButton(onClick = { vm.play(ctx, meta) }) {
                                            Icon(Icons.Filled.PlayArrow, "Lire")
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { vm.play(ctx, meta) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmRemoveWatching) {
        ConfirmRemoveWatching(
            series.name,
            isSeries = true,
            onConfirm = {
                confirmRemoveWatching = false
                vm.removeWatchingSeries(series.id)
                Toast.makeText(ctx, "« ${series.name} » retiré de « En cours »", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmRemoveWatching = false },
        )
    }

    confirmSeason?.let { sn ->
        val toGet = episodes.orEmpty().filter { it.season == sn && episodeKey(it) !in dlKeys }
        AlertDialog(
            onDismissRequest = { confirmSeason = null },
            title = { Text("Télécharger la saison $sn ?") },
            text = {
                Text(
                    "${toGet.size} épisode(s) à télécharger. Ils seront téléchargés l'un après l'autre " +
                        "(beaucoup de fournisseurs n'acceptent qu'une connexion à la fois), même appli fermée.\n\n" +
                        "Pendant ce temps, la lecture en ligne peut être refusée par le fournisseur. " +
                        "Suivi et annulation dans l'onglet « Hors ligne »."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = vm.downloads.enqueue(toGet.map { queued(it) })
                    dlKeys = dlKeys + toGet.map { episodeKey(it) }
                    confirmSeason = null
                    Toast.makeText(ctx, "$n épisode(s) ajouté(s) à « Hors ligne »", Toast.LENGTH_SHORT).show()
                }) { Text("Télécharger") }
            },
            dismissButton = { TextButton(onClick = { confirmSeason = null }) { Text("Annuler") } },
        )
    }
}
