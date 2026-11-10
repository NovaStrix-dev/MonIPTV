package com.perso.moniptv

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Épisode téléchargé, avec sa saison et son numéro lus dans le titre « Série - SxxEyy - Titre ». */
private data class DlEpisode(val info: DownloadInfo, val season: Int, val number: Int, val label: String)

/** Une série de l'écran Hors ligne : ses épisodes regroupés par saison (0 = saison inconnue). */
private data class DlSeries(
    val id: String,
    val name: String,
    val icon: String?,
    val seasons: List<Pair<Int, List<DlEpisode>>>,
) {
    val count get() = seasons.sumOf { it.second.size }
}

private val EPISODE_TITLE = Regex("""^(.*?) - S(\d+)E(\d+)(?: - (.*))?$""")

/** Sépare films et épisodes, puis regroupe les épisodes par série et par saison. */
private fun group(list: List<DownloadInfo>, catalog: Catalog?): Pair<List<DownloadInfo>, List<DlSeries>> {
    val movies = list.filter { !it.record.key.startsWith("ep:") }
    val known = catalog?.series?.associateBy { it.id }.orEmpty()
    val series = list.filter { it.record.key.startsWith("ep:") }
        .groupBy { it.record.key.split(':').getOrElse(1) { "" } }
        .map { (id, items) ->
            var parsedName: String? = null
            val episodes = items.map { d ->
                val m = EPISODE_TITLE.find(d.record.title)
                if (m == null) {
                    DlEpisode(d, 0, 0, d.record.title)
                } else {
                    if (parsedName == null) parsedName = m.groupValues[1]
                    val s = m.groupValues[2].toIntOrNull() ?: 0
                    val e = m.groupValues[3].toIntOrNull() ?: 0
                    val t = m.groupValues[4]
                    DlEpisode(d, s, e, "S%02dE%02d".format(s, e) + if (t.isNotBlank()) " · $t" else "")
                }
            }
            val cat = known[id]
            DlSeries(
                id = id,
                name = cat?.name ?: parsedName ?: items.first().record.title,
                icon = cat?.icon ?: items.first().record.icon,
                seasons = episodes.groupBy { it.season }
                    .toSortedMap(compareBy { if (it == 0) Int.MAX_VALUE else it })
                    .map { (s, eps) -> s to eps.sortedWith(compareBy({ it.number }, { it.label })) },
            )
        }
    return movies to series
}

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<DownloadInfo>?>(null) }
    var tick by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<DownloadInfo?>(null) }
    // Séries repliées (ids séparés par un saut de ligne, pour survivre au changement d'onglet).
    var collapsedRaw by rememberSaveable { mutableStateOf("") }
    val collapsed = collapsedRaw.split('\n').filter { it.isNotEmpty() }.toSet()

    LaunchedEffect(tick) {
        while (true) {
            list = withContext(Dispatchers.IO) { vm.downloads.list() }
            delay(1000)
        }
    }

    val current = list ?: return
    if (current.isEmpty()) {
        CenterMessage("Aucun téléchargement.\n\nOuvrez un film ou un épisode et appuyez sur « Télécharger » pour le regarder ensuite sans connexion.")
        return
    }
    val (movies, series) = group(current, vm.catalog)

    fun play(d: DownloadInfo) {
        // Même progression que la lecture en ligne (section « En cours »).
        val r = d.record
        val isEp = r.key.startsWith("ep:")
        val meta = vm.watching.firstOrNull { it.meta.key == r.key }?.meta ?: WatchMeta(
            key = r.key,
            type = if (isEp) ContentType.SERIES else ContentType.MOVIE,
            title = r.title,
            subtitle = null,
            icon = r.icon,
            url = Uri.fromFile(d.file).toString(),
            seriesId = if (isEp) r.key.split(':').getOrNull(1) else null,
        )
        PlayerActivity.start(ctx, Uri.fromFile(d.file).toString(), r.title, live = false, meta = meta)
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (movies.isNotEmpty()) {
            item(key = "h_movies") { GroupTitle("Films (${movies.size})") }
            items(movies, key = { "m_" + it.record.key + "_" + it.record.downloadId }) { d ->
                DownloadRow(d, d.record.title, showPoster = true, onPlay = { play(d) }, onDelete = { toDelete = d })
            }
        }
        if (series.isNotEmpty()) {
            item(key = "h_series") { GroupTitle("Séries (${series.size})") }
            series.forEach { s ->
                val isCollapsed = s.id in collapsed
                item(key = "s_" + s.id) {
                    SeriesHeader(s, isCollapsed) {
                        collapsedRaw = (if (isCollapsed) collapsed - s.id else collapsed + s.id).joinToString("\n")
                    }
                }
                if (!isCollapsed) {
                    s.seasons.forEach { (season, eps) ->
                        item(key = "se_${s.id}_$season") {
                            Text(
                                if (season == 0) "Autres épisodes" else "Saison $season",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            )
                        }
                        items(eps, key = { "e_" + it.info.record.key + "_" + it.info.record.downloadId }) { e ->
                            DownloadRow(
                                e.info, e.label, showPoster = false,
                                onPlay = { play(e.info) }, onDelete = { toDelete = e.info },
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (d.state == DlState.QUEUED) "Retirer de la file ?" else "Supprimer ?") },
            text = { Text(d.record.title) },
            confirmButton = {
                TextButton(onClick = {
                    vm.downloads.delete(d.record)
                    toDelete = null
                    tick++
                }) { Text(if (d.state == DlState.QUEUED) "Retirer" else "Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

/** En-tête d'une série : affiche, nom, nombre d'épisodes et place prise. Toucher replie / déplie. */
@Composable
private fun SeriesHeader(s: DlSeries, collapsed: Boolean, onToggle: () -> Unit) {
    val done = s.seasons.flatMap { it.second }.filter { it.info.state == DlState.DONE }
    val size = done.sumOf { it.info.file.length() }
    val waiting = s.count - done.size
    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(s.icon, s.name, Modifier.width(54.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val seasons = s.seasons.count { it.first != 0 }
                val parts = buildList {
                    if (seasons > 0) add(if (seasons > 1) "$seasons saisons" else "1 saison")
                    add(if (s.count > 1) "${s.count} épisodes" else "1 épisode")
                    if (size > 0) add(formatSize(size))
                    if (waiting > 0) add("$waiting en cours ou en file")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            }
            Icon(
                if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                if (collapsed) "Déplier" else "Replier",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun DownloadRow(
    d: DownloadInfo,
    title: String,
    showPoster: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showPoster) {
                Poster(d.record.icon, d.record.title, Modifier.width(54.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                when (d.state) {
                    DlState.RUNNING -> {
                        LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(d.progress * 100).toInt()} %", style = MaterialTheme.typography.labelSmall)
                    }
                    DlState.QUEUED -> Text(
                        if (d.queuePos == 1) "En file d'attente · prochain" else "En file d'attente · n° ${d.queuePos}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    DlState.PENDING -> Text("En attente…", style = MaterialTheme.typography.labelSmall)
                    DlState.PAUSED -> Text("En pause (attente du réseau)", style = MaterialTheme.typography.labelSmall)
                    DlState.DONE -> Text("Disponible hors ligne · ${formatSize(d.file.length())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    DlState.FAILED -> Text("Échec du téléchargement", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (d.state == DlState.DONE) {
                IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Lire") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer") }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f Go".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f Mo".format(bytes / 1_048_576.0)
    else -> "${bytes / 1024} Ko"
}
