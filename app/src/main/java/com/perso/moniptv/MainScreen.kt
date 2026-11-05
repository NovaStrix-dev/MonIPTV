package com.perso.moniptv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab(val label: String, val icon: ImageVector) {
    LIVE("TV", Icons.Filled.LiveTv),
    MOVIES("Films", Icons.Filled.Movie),
    SERIES("Séries", Icons.Filled.VideoLibrary),
    SEARCH("Recherche", Icons.Filled.Search),
    DOWNLOADS("Hors ligne", Icons.Filled.Download),
}

/** Identifiant interne de la catégorie « Nouveautés ». */
private const val NEW_CAT = "__nouveautes__"

/** Nombre de titres listés dans « Nouveautés ». */
private const val NEW_LIMIT = 200

/** Aperçu des nouveautés en tête de la vue « Toutes » (12 = lignes complètes à 2, 3, 4 ou 6 colonnes). */
private const val NEW_PREVIEW = 12

/** Largeur minimale d'une affiche dans la grille : 3 colonnes sur la plupart des téléphones. */
val POSTER_MIN_WIDTH = 104.dp

private val dayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private val dateTimeFormat = SimpleDateFormat("dd/MM à HH:mm", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.MOVIES) }
    var openSeries by remember { mutableStateOf<Media?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    // Résultat d'une actualisation : message bref.
    LaunchedEffect(vm.info) {
        vm.info?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.info = null
        }
    }

    val series = openSeries
    if (series != null) {
        BackHandler { openSeries = null }
        SeriesScreen(vm, series, onBack = { openSeries = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.LIVE -> "TV en direct"
                            Tab.MOVIES -> "Films"
                            Tab.SERIES -> "Séries"
                            Tab.SEARCH -> "Recherche"
                            Tab.DOWNLOADS -> "Hors ligne"
                        }
                    )
                },
                actions = {
                    // Bouton d'actualisation à la demande.
                    TextButton(onClick = { vm.refresh() }, enabled = !vm.loading) {
                        if (vm.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (vm.loading) "Mise à jour…" else "Actualiser")
                    }
                    IconButton(onClick = { confirmLogout = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Changer de compte")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val c = vm.catalog
            if (vm.loading && c != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (vm.error != null && c != null && tab != Tab.DOWNLOADS) {
                Text(
                    "Actualisation impossible : ${vm.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable { vm.error = null }.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            when {
                tab == Tab.DOWNLOADS -> DownloadsScreen(vm)
                c == null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (vm.loading) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Chargement du catalogue…")
                    } else {
                        Text(
                            (vm.error ?: "Catalogue indisponible.") +
                                "\n\nVos téléchargements restent accessibles dans « Hors ligne ».",
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { vm.refresh() }) { Text("Réessayer") }
                    }
                }
                tab == Tab.LIVE -> BrowseScreen(vm, c.liveCats, c.live, ContentType.LIVE, c.updatedAt) {}
                tab == Tab.MOVIES -> BrowseScreen(vm, c.movieCats, c.movies, ContentType.MOVIE, c.updatedAt) {}
                tab == Tab.SERIES -> BrowseScreen(vm, c.seriesCats, c.series, ContentType.SERIES, c.updatedAt) { openSeries = it }
                tab == Tab.SEARCH -> SearchScreen(vm, c) { openSeries = it }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Changer de compte ?") },
            text = { Text("Les identifiants enregistrés seront effacés. Les fichiers téléchargés sont conservés.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Déconnecter") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun CenterMessage(text: String, loading: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(text, textAlign = TextAlign.Center)
    }
}

/** Un bloc de la liste : un titre de catégorie et ses films / séries. */
private data class Section(
    val title: String,
    val items: List<Media>,
    /** Si non nul : aperçu, avec un bouton « Tout voir » qui ouvre cette catégorie. */
    val seeAll: String? = null,
    val showDates: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    vm: AppViewModel,
    categories: List<Category>,
    items: List<Media>,
    type: ContentType,
    updatedAt: Long,
    onOpenSeries: (Media) -> Unit,
) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMovie by remember { mutableStateOf<Media?>(null) }
    val listState = rememberLazyListState()

    val catNames = remember(categories) { categories.associate { it.id to it.name } }

    // Nouveautés : les titres les plus récemment ajoutés (films et séries uniquement).
    val recent by produceState(initialValue = emptyList<Media>(), items, type) {
        value = if (type == ContentType.LIVE) emptyList() else withContext(Dispatchers.Default) {
            items.filter { it.added > 0 }.sortedByDescending { it.added }.take(NEW_LIMIT)
        }
    }

    val sections by produceState<List<Section>?>(initialValue = null, items, query, cat, recent) {
        value = withContext(Dispatchers.Default) {
            val words = queryWords(query)
            when {
                words.isNotEmpty() -> listOf(Section("", items.filter { it.matches(words) }))
                cat == NEW_CAT -> listOf(Section("", recent, showDates = true))
                cat != null -> listOf(Section("", items.filter { it.categoryId == cat }))
                type == ContentType.LIVE -> listOf(Section("", items))
                else -> {
                    // « Toutes » : films / séries rangés par catégorie, Nouveautés en tête.
                    val out = ArrayList<Section>()
                    if (recent.isNotEmpty()) {
                        out.add(Section("Nouveautés", recent.take(NEW_PREVIEW), seeAll = NEW_CAT, showDates = true))
                    }
                    val byCat = items.groupBy { it.categoryId }
                    categories.forEach { c -> byCat[c.id]?.let { out.add(Section(c.name, it)) } }
                    val known = categories.mapTo(HashSet()) { it.id }
                    val others = items.filter { it.categoryId !in known }
                    if (others.isNotEmpty()) out.add(Section("Autres", others))
                    out
                }
            }
        }
    }

    val gridState = rememberLazyGridState()

    // Remonte en haut de la liste quand on change de catégorie ou de recherche.
    LaunchedEffect(cat, query) {
        runCatching { listState.scrollToItem(0) }
        runCatching { gridState.scrollToItem(0) }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Rechercher…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Effacer") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (query.isBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("Toutes") }) }
                if (recent.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = cat == NEW_CAT,
                            onClick = { cat = NEW_CAT },
                            label = { Text("Nouveautés") },
                            leadingIcon = { Icon(Icons.Filled.NewReleases, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                items(categories) { c ->
                    FilterChip(
                        selected = cat == c.id,
                        onClick = { cat = c.id },
                        label = { Text(c.name, maxLines = 1) },
                    )
                }
            }
        }

        val list = sections
        val total = list?.filter { it.seeAll == null }?.sumOf { it.items.size } ?: 0
        Text(
            buildString {
                append("$total titre(s)")
                if (updatedAt > 0) append(" · mis à jour le ").append(dateTimeFormat.format(Date(updatedAt)))
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )

        when {
            list == null -> CenterMessage("", loading = true)
            list.all { it.items.isEmpty() } -> CenterMessage(
                if (cat == NEW_CAT) "Aucune nouveauté pour le moment." else "Aucun résultat"
            )
            type == ContentType.LIVE -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(list.flatMap { it.items }) { m ->
                    LiveRow(m) { m.url?.let { PlayerActivity.start(ctx, it, m.name, live = true) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            // Films et séries : grille d'affiches, titre sous chaque affiche, blocs par catégorie.
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(POSTER_MIN_WIDTH),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                list.forEach { s ->
                    if (s.title.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                if (s.seeAll == null) "${s.title} (${s.items.size})" else s.title,
                                onSeeAll = s.seeAll?.let { target -> { cat = target } },
                            )
                        }
                    }
                    gridItems(s.items) { m ->
                        val subtitle = if (s.showDates && m.added > 0) {
                            "Ajouté le " + dayFormat.format(Date(m.added * 1000))
                        } else {
                            null
                        }
                        PosterCard(m, subtitle) {
                            if (type == ContentType.SERIES) onOpenSeries(m) else selectedMovie = m
                        }
                    }
                }
            }
        }
    }

    selectedMovie?.let { m -> MovieDialog(vm, m) { selectedMovie = null } }
}

/** Fenêtre d'un film : Lire ou Télécharger. */
@Composable
fun MovieDialog(vm: AppViewModel, m: Media, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val key = "movie:${m.stableKey}"
    val already = remember(m) { vm.downloads.isDownloaded(key) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(m.name) },
        text = {
            Row {
                Poster(m.icon, m.name, Modifier.width(90.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (already) "Ce film est dans vos téléchargements (onglet Hors ligne)."
                    else "Regarder maintenant ou télécharger pour plus tard ?"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                m.url?.let { PlayerActivity.start(ctx, it, m.name, live = false) }
            }) { Text("Lire") }
        },
        dismissButton = {
            if (!already) {
                TextButton(onClick = {
                    onDismiss()
                    m.url?.let { url ->
                        vm.downloads.start(key, m.name, m.icon, url, m.ext ?: "mp4")
                        Toast.makeText(ctx, "Téléchargement lancé", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Télécharger") }
            }
        },
    )
}

@Composable
fun LiveRow(m: Media, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (m.icon != null) {
                AsyncImage(model = m.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            } else {
                Text(m.name.take(2).uppercase(), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Poster(icon: String?, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(18), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
        if (icon != null) {
            AsyncImage(model = icon, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Titre d'un bloc de la grille (catégorie), avec « Tout voir » en option. */
@Composable
fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("Tout voir") }
        }
    }
}

/** Case de la grille : l'affiche, puis le titre dessous (et la date d'ajout pour les nouveautés). */
@Composable
fun PosterCard(m: Media, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Poster(m.icon, m.name, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            m.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/** Ligne de liste : affiche à gauche, titre et sous-titre (catégorie ou date d'ajout). */
@Composable
fun PosterRow(m: Media, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(m.icon, m.name, Modifier.width(80.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
