package com.perso.moniptv

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<DownloadInfo>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<DownloadInfo?>(null) }

    LaunchedEffect(tick) {
        while (true) {
            list = withContext(Dispatchers.IO) { vm.downloads.list() }
            delay(1000)
        }
    }

    if (list.isEmpty()) {
        CenterMessage("Aucun téléchargement.\n\nOuvrez un film ou un épisode et appuyez sur « Télécharger » pour le regarder ensuite sans connexion.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(list) { d ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(d.record.icon, d.record.title, Modifier.width(54.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.record.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        IconButton(onClick = {
                            // Même progression que la lecture en ligne (section « En cours »).
                            val r = d.record
                            val meta = vm.watching.firstOrNull { it.meta.key == r.key }?.meta ?: WatchMeta(
                                key = r.key,
                                type = if (r.key.startsWith("ep:")) ContentType.SERIES else ContentType.MOVIE,
                                title = r.title,
                                subtitle = null,
                                icon = r.icon,
                                url = Uri.fromFile(d.file).toString(),
                                seriesId = if (r.key.startsWith("ep:")) r.key.split(':').getOrNull(1) else null,
                            )
                            PlayerActivity.start(ctx, Uri.fromFile(d.file).toString(), r.title, live = false, meta = meta)
                        }) { Icon(Icons.Filled.PlayArrow, "Lire") }
                    }
                    IconButton(onClick = { toDelete = d }) { Icon(Icons.Filled.Delete, "Supprimer") }
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

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f Go".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f Mo".format(bytes / 1_048_576.0)
    else -> "${bytes / 1024} Ko"
}
