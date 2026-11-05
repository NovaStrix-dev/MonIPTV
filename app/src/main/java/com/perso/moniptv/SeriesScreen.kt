package com.perso.moniptv

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
    var err by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    var dlKeys by remember { mutableStateOf(vm.downloads.records().map { it.key }.toSet()) }

    LaunchedEffect(series.id) {
        try {
            episodes = vm.episodes(series)
        } catch (e: Exception) {
            err = e.message ?: "Erreur de chargement"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
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
                            Text(series.name)
                            Text("${seasons.size} saison(s) · ${eps.size} épisode(s)")
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
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(eps.filter { it.season == current }) { e ->
                            val key = "ep:${series.id}:${e.id}"
                            val label = "S%02dE%02d".format(e.season, e.number)
                            val meta = e.watchMeta(series)
                            val started = vm.watching.firstOrNull { it.meta.key == meta.key }
                            ListItem(
                                headlineContent = { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Column {
                                        Text(if (started != null) "$label · en cours, arrêté à ${formatTime(started.position)}" else label)
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
                                        IconButton(onClick = {
                                            if (key in dlKeys) {
                                                Toast.makeText(ctx, "Déjà dans « Hors ligne »", Toast.LENGTH_SHORT).show()
                                            } else {
                                                vm.downloads.start(key, "${series.name} - $label - ${e.title}", series.icon, e.url, e.ext)
                                                dlKeys = dlKeys + key
                                                Toast.makeText(ctx, "Téléchargement lancé", Toast.LENGTH_SHORT).show()
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
}
