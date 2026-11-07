package com.perso.moniptv

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.BugReport
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab as TabItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
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

/** Identifiant interne de la catégorie « En cours ». */
private const val WATCH_CAT = "__en_cours__"

/** Identifiant interne de la catégorie « Favoris ». */
private const val FAV_CAT = "__favoris__"

/** Identifiant du bloc « Tous les films / Toutes les séries » de la vue « Toutes » (#22). */
private const val ALL_ID = "__tous__"

/** Blocs repliés de la vue « Toutes », mémorisés par onglet (prefs `ui`). */
private object CollapsedSections {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun load(ctx: Context, type: ContentType): Set<String> =
        prefs(ctx).getStringSet("collapsed_" + type.name, emptySet()).orEmpty().toSet()
    fun save(ctx: Context, type: ContentType, ids: Set<String>) {
        prefs(ctx).edit().putStringSet("collapsed_" + type.name, HashSet(ids)).apply()
    }
}

/** Nombre de favoris montrés en tête de la vue « Toutes » avant « Tout voir ». */
private const val FAV_PREVIEW = 12

/** Ajoute ou retire un titre des favoris et le confirme par un message bref. */
fun toggleFavoriteWithToast(ctx: Context, vm: AppViewModel, m: Media) {
    val added = vm.toggleFavorite(m)
    Toast.makeText(
        ctx,
        if (added) "« ${m.name} » ajouté aux favoris" else "« ${m.name} » retiré des favoris",
        Toast.LENGTH_SHORT,
    ).show()
}

/** Nombre de titres « En cours » montrés en tête de la vue « Toutes » (2 lignes de 3). */
private const val WATCH_PREVIEW = 6

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
    var showDiagnostic by remember { mutableStateOf(false) }

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

    if (showDiagnostic) {
        DiagnosticDialog(
            title = "Journal de diagnostic",
            intro = "Derniers événements du lecteur. En cas d'arrêt de l'appli, copiez ce texte et envoyez-le à Claude.",
            report = Diagnostics.fullReport(ctx),
            onDismiss = { showDiagnostic = false },
        )
    }

    Scaffold(
        topBar = {
            Column {
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
                        // Journal de diagnostic (#25) : à copier et envoyer en cas d'arrêt de l'appli.
                        IconButton(onClick = { showDiagnostic = true }) {
                            Icon(Icons.Outlined.BugReport, "Journal de diagnostic")
                        }
                        IconButton(onClick = { confirmLogout = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Changer de compte")
                        }
                    },
                )
                // Barre d'onglets en haut de l'écran (#21), sous la barre de titre.
                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab.entries.forEach { t ->
                        TabItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, null) },
                            text = { Text(t.label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                        )
                    }
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
                VpnBypassButton(vm)
            }
            VpnBypassNotice(vm)
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
                        if (vm.error != null) VpnBypassButton(vm)
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
            text = { Text("Les identifiants enregistrés et la liste « En cours » seront effacés. Les favoris et les fichiers téléchargés sont conservés.") },
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
    /** Identifiant stable du bloc (pour mémoriser s'il est replié). */
    val id: String = title,
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
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMovie by remember { mutableStateOf<Media?>(null) }
    var watchMenu by remember { mutableStateOf<WatchEntry?>(null) }
    var watchToRemove by remember { mutableStateOf<WatchEntry?>(null) }
    val listState = rememberLazyListState()

    // « En cours » : films ou épisodes commencés, pour cet onglet.
    val watching = remember(vm.watching, type) {
        if (type == ContentType.LIVE) emptyList() else vm.watching.filter { it.meta.type == type }
    }
    // « En cours » n'apparaît que dans sa propre catégorie (#23) ; la vue « Toutes » n'a plus de blocs.
    val shownWatching = if (cat == WATCH_CAT) watching else emptyList()

    // Toutes les listes vont du dernier ajouté au premier (titres sans date à la fin, dans leur ordre).
    val sorted = remember(items) { items.newestFirst() }
    // Nombre de titres par catégorie (affiché dans la colonne des catégories).
    val catCounts = remember(items) { items.groupingBy { it.categoryId }.eachCount() }

    // Nouveautés : les titres les plus récemment ajoutés (films et séries uniquement).
    val recent by produceState(initialValue = emptyList<Media>(), items, type) {
        value = if (type == ContentType.LIVE) emptyList() else withContext(Dispatchers.Default) {
            items.filter { it.added > 0 }.sortedByDescending { it.added }.take(NEW_LIMIT)
        }
    }

    // Favoris de cet onglet, du dernier ajouté au plus ancien.
    val favs by produceState(initialValue = emptyList<Media>(), items, type, vm.favorites) {
        val marks = vm.favorites
        value = if (type == ContentType.LIVE || marks.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            items.mapNotNull { m -> marks[Favorites.key(m)]?.let { m to it } }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }

    val sections by produceState<List<Section>?>(initialValue = null, sorted, cat, recent, favs) {
        val items = sorted
        value = withContext(Dispatchers.Default) {
            when {
                cat == WATCH_CAT -> emptyList()
                cat == NEW_CAT -> listOf(Section("", recent, showDates = true))
                cat == FAV_CAT -> listOf(Section("", favs))
                cat != null -> listOf(Section("", items.filter { it.categoryId == cat }))
                type == ContentType.LIVE -> listOf(Section("", items))
                // « Toutes » (#23) : une seule grille avec tous les titres, sans blocs ni titres de section.
                else -> listOf(Section("", items))
            }
        }
    }

    val gridState = rememberLazyGridState()

    // Blocs repliés de la vue « Toutes » (#15) : toucher le titre d'un bloc le replie / déplie.
    // Mémorisé par onglet sur le téléphone.
    val collapsed = remember(type) { mutableStateListOf<String>().apply { addAll(CollapsedSections.load(ctx, type)) } }
    fun toggleCollapsed(id: String) {
        if (id in collapsed) collapsed.remove(id) else collapsed.add(id)
        CollapsedSections.save(ctx, type, collapsed.toSet())
    }
    val allSectionIds = buildList {
        if (shownWatching.isNotEmpty() && cat == null) add(WATCH_CAT)
        sections?.forEach { if (it.title.isNotEmpty()) add(it.id) }
    }
    val canCollapse = cat == null && type != ContentType.LIVE && allSectionIds.isNotEmpty()
    val allCollapsed = canCollapse && allSectionIds.all { it in collapsed }

    // Remonte en haut de la liste quand on change de catégorie.
    LaunchedEffect(cat) {
        runCatching { listState.scrollToItem(0) }
        runCatching { gridState.scrollToItem(0) }
    }

    // Catégories en colonne à gauche (#22), pour les films, les séries et les chaînes TV.
    Row(Modifier.fillMaxSize()) {
        CategoryColumn(
            selected = cat,
            categories = categories,
            counts = catCounts,
            total = items.size,
            watchingCount = if (type == ContentType.LIVE) -1 else watching.size,
            favCount = if (type == ContentType.LIVE) -1 else favs.size,
            recentCount = recent.size,
            onSelect = { cat = it },
        )
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val list = sections
            val total = if (cat == WATCH_CAT) watching.size else list?.filter { it.seeAll == null }?.sumOf { it.items.size } ?: 0
            Row(
                Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append("$total titre(s)")
                        if (updatedAt > 0) append(" · mis à jour le ").append(dateTimeFormat.format(Date(updatedAt)))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (canCollapse) {
                    Text(
                        if (allCollapsed) "Tout déplier" else "Tout replier",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (allCollapsed) collapsed.removeAll(allSectionIds)
                                else allSectionIds.forEach { if (it !in collapsed) collapsed.add(it) }
                                CollapsedSections.save(ctx, type, collapsed.toSet())
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            when {
                list == null -> CenterMessage("", loading = true)
                list.all { it.items.isEmpty() } && shownWatching.isEmpty() -> CenterMessage(
                    when (cat) {
                        WATCH_CAT -> "Rien en cours pour le moment.\n\nUn film ou un épisode apparaît ici après 20 secondes de visionnage."
                    NEW_CAT -> "Aucune nouveauté pour le moment."
                        FAV_CAT -> "Aucun favori pour l'instant.\n\nTouchez le petit cœur en haut à droite d'une affiche, " +
                            "ou celui de la fiche d'un film ou d'une série."
                        else -> "Aucun résultat"
                    }
                )
                type == ContentType.LIVE -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    // Zapping (#17) : le lecteur reçoit les chaînes affichées (catégorie choisie ou recherche)
                    // pour passer à la chaîne suivante / précédente sans quitter cette catégorie.
                    val channels = list.flatMap { it.items }
                    val groupName = when {
                        cat == null -> "Toutes les chaînes"
                        else -> categories.firstOrNull { it.id == cat }?.name ?: "Catégorie"
                    }
                    items(channels) { m ->
                        LiveRow(m) { m.url?.let { PlayerActivity.startLive(ctx, channels, m, groupName) } }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                // Films et séries : grille d'affiches, titre sous chaque affiche.
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(POSTER_MIN_WIDTH),
                    state = gridState,
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (shownWatching.isNotEmpty()) {
                        val folded = cat == null && WATCH_CAT in collapsed
                        if (cat != WATCH_CAT) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(
                                    "En cours",
                                    onSeeAll = if (watching.size > WATCH_PREVIEW) ({ cat = WATCH_CAT }) else null,
                                    collapsed = folded,
                                    onToggle = { toggleCollapsed(WATCH_CAT) },
                                )
                            }
                        }
                        if (!folded) gridItems(shownWatching, key = { "w:" + it.meta.key }) { e ->
                            WatchCard(
                                e,
                                onClick = { vm.play(ctx, e.meta) },
                                onLongClick = { watchMenu = e },
                                onRemove = { watchToRemove = e },
                            )
                        }
                    }
                    list.forEach { s ->
                        val folded = s.title.isNotEmpty() && s.id in collapsed
                        if (s.title.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(
                                    if (s.seeAll == null) "${s.title} (${s.items.size})" else s.title,
                                    onSeeAll = s.seeAll?.let { target -> { cat = target } },
                                    collapsed = folded,
                                    onToggle = { toggleCollapsed(s.id) },
                                )
                            }
                        }
                        if (!folded) gridItems(s.items) { m ->
                            val subtitle = if (s.showDates && m.added > 0) {
                                "Ajouté le " + dayFormat.format(Date(m.added * 1000))
                            } else {
                                null
                            }
                            PosterCard(
                                m, subtitle,
                                favorite = vm.isFavorite(m),
                                onToggleFavorite = { toggleFavoriteWithToast(ctx, vm, m) },
                                watched = vm.isWatched(m),
                            ) {
                                if (type == ContentType.SERIES) onOpenSeries(m) else selectedMovie = m
                            }
                        }
                    }
                }
            }
    }
    }

    selectedMovie?.let { m -> MovieDialog(vm, m) { selectedMovie = null } }

    watchMenu?.let { e ->
        val series = if (e.meta.seriesId != null) items.firstOrNull { it.id == e.meta.seriesId } else null
        WatchMenuDialog(
            e,
            onResume = { watchMenu = null; vm.play(ctx, e.meta) },
            onOpenSeries = series?.let { s -> { watchMenu = null; onOpenSeries(s) } },
            onRemove = { watchMenu = null; watchToRemove = e },
            onDismiss = { watchMenu = null },
        )
    }

    watchToRemove?.let { e ->
        ConfirmRemoveWatching(
            e.meta.title,
            isSeries = e.meta.seriesId != null,
            onConfirm = {
                watchToRemove = null
                vm.removeWatching(e.meta.key)
                Toast.makeText(ctx, "« ${e.meta.title} » retiré de « En cours »", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { watchToRemove = null },
        )
    }
}

/** Confirmation avant de retirer un film ou une série de « En cours ». */
@Composable
fun ConfirmRemoveWatching(title: String, isSeries: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retirer de « En cours » ?") },
        text = {
            Text(
                if (isSeries) "La série « $title » ne sera plus affichée dans « En cours ». " +
                    "Elle y reviendra si vous regardez un de ses épisodes."
                else "« $title » ne sera plus affiché dans « En cours ». Le film repartira du début."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Retirer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Menu d'un titre « En cours » (appui long) : reprendre, voir la série, retirer. */
@Composable
fun WatchMenuDialog(
    e: WatchEntry,
    onResume: () -> Unit,
    onOpenSeries: (() -> Unit)?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.meta.title) },
        text = {
            Column {
                e.meta.subtitle?.let { Text(it) }
                Text("Arrêté à ${formatTime(e.position)}" + if (e.duration > 0) " sur ${formatTime(e.duration)}" else "")
                Spacer(Modifier.height(8.dp))
                if (onOpenSeries != null) {
                    TextButton(onClick = onOpenSeries) { Text("Voir la série") }
                }
                TextButton(onClick = onRemove) { Text("Retirer de « En cours »") }
            }
        },
        confirmButton = { TextButton(onClick = onResume) { Text("Reprendre") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Case « En cours » : l'affiche avec une barre de progression, le titre et le temps restant. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchCard(e: WatchEntry, onClick: () -> Unit, onLongClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            Poster(e.meta.icon, e.meta.title, Modifier.fillMaxWidth())
            LinearProgressIndicator(
                progress = { e.progress },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp),
                trackColor = Color.Black.copy(alpha = 0.55f),
            )
            if (onRemove != null) {
                // Bouton « retirer » en haut à droite (zone tactile de 40 dp, pastille de 26 dp), comme le cœur des affiches.
                Box(
                    Modifier.align(Alignment.TopEnd).size(40.dp).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            "Retirer de « En cours »",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            e.meta.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        val info = listOfNotNull(
            e.meta.subtitle?.substringBefore(" · "),
            if (e.duration > 0) "reste ${e.remainingMin} min" else formatTime(e.position),
        ).joinToString(" · ")
        Text(
            info,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/** 1:02:03 ou 12:34. */
fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Fiche d'un film : synopsis, Lire ou Télécharger, favori. */
@Composable
fun MovieDialog(vm: AppViewModel, m: Media, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val key = "movie:${m.stableKey}"
    val already = remember(m) { vm.downloads.isDownloaded(key) }
    val inProgress = remember(m, vm.watching) { vm.watching.firstOrNull { it.meta.key == key } }
    var details by remember(m) { mutableStateOf<MediaDetails?>(null) }
    var loadingDetails by remember(m) { mutableStateOf(vm.account?.type == AccountType.XTREAM) }
    LaunchedEffect(m) {
        details = vm.movieDetails(m)
        loadingDetails = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, Modifier.weight(1f))
                WatchedButton(vm.isWatched(m)) { toggleWatchedWithToast(ctx, vm, m) }
                FavoriteButton(vm.isFavorite(m)) { toggleFavoriteWithToast(ctx, vm, m) }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Row {
                    Poster(m.icon, m.name, Modifier.width(90.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        vm.watched[Favorites.key(m)]?.let { at ->
                            WatchedBadge()
                            Text(
                                "Déjà vu le " + dayFormat.format(Date(at)),
                                style = MaterialTheme.typography.labelSmall,
                                color = WATCHED_BLUE,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                            )
                        }
                        // Catégorie du film (#22), avec le synopsis.
                        vm.catalog?.categoryName(m)?.let { CategoryLabel(it) }
                        details?.facts?.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            if (already) "Ce film est dans vos téléchargements (onglet Hors ligne)."
                            else "Regarder maintenant ou télécharger pour plus tard ?",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (inProgress != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Déjà commencé : reprise à ${formatTime(inProgress.position)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                // Synopsis (chargé à l'ouverture de la fiche).
                when {
                    loadingDetails -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Chargement du résumé…", style = MaterialTheme.typography.bodySmall)
                    }
                    !details?.plot.isNullOrBlank() -> {
                        Spacer(Modifier.height(12.dp))
                        Text("Synopsis", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(details?.plot ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                m.watchMeta()?.let { vm.play(ctx, it) }
            }) { Text(if (inProgress != null) "Reprendre" else "Lire") }
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

/**
 * Titre d'un bloc de la grille (catégorie), avec « Tout voir » en option.
 * Si [onToggle] est fourni, toucher le titre replie / déplie le bloc (flèche à gauche).
 */
@Composable
fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggle != null) {
                Icon(
                    if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                    if (collapsed) "Déplier" else "Replier",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (onSeeAll != null && !collapsed) {
            TextButton(onClick = onSeeAll) { Text("Tout voir") }
        }
    }
}

/**
 * Case de la grille : l'affiche, puis le titre dessous (et la date d'ajout pour les nouveautés).
 * Un cœur en haut à droite de l'affiche ajoute ou retire le titre des favoris d'un simple toucher
 * (plein et rouge = favori, contour discret sinon). L'appui long sur l'affiche fait la même chose.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    m: Media,
    subtitle: String?,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    watched: Boolean = false,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onToggleFavorite)) {
        Box {
            Poster(m.icon, m.name, Modifier.fillMaxWidth())
            if (watched) WatchedBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            if (onToggleFavorite != null || favorite) {
                // Zone tactile de 40 dp, pastille visible de 26 dp.
                Box(
                    Modifier.align(Alignment.TopEnd).size(40.dp)
                        .then(if (onToggleFavorite != null) Modifier.clickable(onClick = onToggleFavorite) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = if (favorite) FAV_RED else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
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

/** Catégorie d'un film ou d'une série, affichée dans sa fiche (#22). */
@Composable
fun CategoryLabel(name: String, modifier: Modifier = Modifier) {
    Text(
        "Catégorie : $name",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/** Rouge des cœurs de favoris. */
val FAV_RED = Color(0xFFFF5A6E)

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


/** Fond de la colonne des catégories et couleur de la catégorie choisie (charte de l'appli). */
private val CAT_COLUMN_BG = Color(0xFF151B26)
private val CAT_SELECTED_BG = Color(0xFF26365A)

/** Largeur de la colonne des catégories : laisse 2 affiches par ligne sur un téléphone en portrait. */
private val CAT_COLUMN_WIDTH = 116.dp

/**
 * Catégories en colonne sur la gauche de l'écran (#22), à la place de la liste déroulante (films, séries)
 * et de la rangée de boutons (TV). [favCount] négatif : pas de favoris (chaînes TV).
 */
@Composable
private fun CategoryColumn(
    selected: String?,
    categories: List<Category>,
    counts: Map<String, Int>,
    total: Int,
    watchingCount: Int,
    favCount: Int,
    recentCount: Int,
    onSelect: (String?) -> Unit,
) {
    LazyColumn(
        Modifier.width(CAT_COLUMN_WIDTH).fillMaxHeight().background(CAT_COLUMN_BG),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        item { CategoryItem("Toutes", total, selected == null) { onSelect(null) } }
        // « En cours » toujours affiché au-dessus de « Favoris », même à 0 (#23). Négatif : chaînes TV, pas de « En cours ».
        if (watchingCount >= 0) item { CategoryItem("En cours", watchingCount, selected == WATCH_CAT, Icons.Filled.History) { onSelect(WATCH_CAT) } }
        if (favCount >= 0) item { CategoryItem("Favoris", favCount, selected == FAV_CAT, Icons.Filled.Favorite) { onSelect(FAV_CAT) } }
        if (recentCount > 0) item { CategoryItem("Nouveautés", recentCount, selected == NEW_CAT, Icons.Filled.NewReleases) { onSelect(NEW_CAT) } }
        item { HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) }
        items(categories, key = { "c:" + it.id }) { c ->
            CategoryItem(c.name, counts[c.id] ?: 0, selected == c.id) { onSelect(c.id) }
        }
    }
}

/** Une ligne de la colonne des catégories : nom (2 lignes max.) et nombre de titres. */
@Composable
private fun CategoryItem(name: String, count: Int, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CAT_SELECTED_BG else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon, null, Modifier.size(14.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cœur plein (favori) ou vide, pour ajouter / retirer un titre des favoris. */
/** Pastille « Vu » en haut à gauche d'une affiche (#19). */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(WATCHED_BLUE.copy(alpha = 0.92f))
            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text("Vu", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

val WATCHED_BLUE = Color(0xFF4F8CFF)

/** Bouton « Déjà vu » (coche) des fiches film et série (#19). */
@Composable
fun WatchedButton(watched: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = if (watched) "Marquer comme non vu" else "Marquer comme vu",
            tint = if (watched) WATCHED_BLUE else MaterialTheme.colorScheme.onSurface,
        )
    }
}

fun toggleWatchedWithToast(ctx: Context, vm: AppViewModel, m: Media, episodeKeys: List<String> = emptyList()) {
    val now = !vm.isWatched(m)
    vm.setWatched(m, now, episodeKeys)
    Toast.makeText(
        ctx,
        if (now) "« ${m.name} » marqué comme vu" else "« ${m.name} » marqué comme non vu",
        Toast.LENGTH_SHORT,
    ).show()
}

@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (favorite) FAV_RED else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Champ de recherche compact (40 dp de haut, coins arrondis), loupe et bouton d'effacement. */
@Composable
fun CompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        modifier = modifier.then(fieldModifier),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Clear, "Effacer", Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

/** Proposé après une erreur quand un VPN est actif : faire passer MonIPTV à côté du VPN (issue #6). */
@Composable
fun VpnBypassButton(vm: AppViewModel, onEnabled: () -> Unit = { vm.refresh() }) {
    if (vm.bypassVpn || !vm.vpnActive()) return
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text("Contourner le VPN pour MonIPTV")
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Contourner le VPN ?") },
            text = {
                Text(
                    "MonIPTV utilisera directement le Wi-Fi ou les données mobiles, sans passer par le VPN " +
                        "(comme les téléchargements). Les autres applis restent protégées par le VPN. " +
                        "Votre fournisseur IPTV verra votre adresse réelle.\n\n" +
                        "Certaines applis VPN interdisent ce contournement : dans ce cas, excluez MonIPTV " +
                        "dans les réglages du VPN (« tunnel fractionné »)."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.changeBypassVpn(true); onEnabled() }) { Text("Contourner") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        )
    }
}

/** Rappel discret quand le contournement du VPN est activé, avec la possibilité de l'arrêter. */
@Composable
fun VpnBypassNotice(vm: AppViewModel) {
    if (!vm.bypassVpn || !vm.vpnActive()) return
    Text(
        "MonIPTV passe à côté du VPN · toucher pour arrêter",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().clickable { vm.changeBypassVpn(false) }.padding(horizontal = 14.dp, vertical = 2.dp),
    )
}
