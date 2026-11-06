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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
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

/** Identifiant du bloc « Autres » (titres sans catégorie connue). */
private const val OTHERS_ID = "__autres__"

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
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMovie by remember { mutableStateOf<Media?>(null) }
    var watchMenu by remember { mutableStateOf<WatchEntry?>(null) }
    val listState = rememberLazyListState()

    // « En cours » : films ou épisodes commencés, pour cet onglet.
    val watching = remember(vm.watching, type) {
        if (type == ContentType.LIVE) emptyList() else vm.watching.filter { it.meta.type == type }
    }
    // Si la liste « En cours » se vide pendant qu'on la regarde, on revient à « Toutes ».
    LaunchedEffect(watching.isEmpty()) { if (watching.isEmpty() && cat == WATCH_CAT) cat = null }
    val shownWatching = when {
        query.isNotBlank() -> emptyList()
        cat == WATCH_CAT -> watching
        cat == null -> watching.take(WATCH_PREVIEW)
        else -> emptyList()
    }

    // Toutes les listes vont du dernier ajouté au premier (titres sans date à la fin, dans leur ordre).
    val sorted = remember(items) { items.newestFirst() }
    // Nombre de titres par catégorie (affiché dans la liste déroulante des films).
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

    val sections by produceState<List<Section>?>(initialValue = null, sorted, query, cat, recent, favs) {
        val items = sorted
        value = withContext(Dispatchers.Default) {
            val words = queryWords(query)
            when {
                words.isNotEmpty() -> listOf(Section("", items.filter { it.matches(words) }))
                cat == WATCH_CAT -> emptyList()
                cat == NEW_CAT -> listOf(Section("", recent, showDates = true))
                cat == FAV_CAT -> listOf(Section("", favs))
                cat != null -> listOf(Section("", items.filter { it.categoryId == cat }))
                type == ContentType.LIVE -> listOf(Section("", items))
                else -> {
                    // « Toutes » : films / séries rangés par catégorie, Favoris et Nouveautés en tête.
                    val out = ArrayList<Section>()
                    if (favs.isNotEmpty()) {
                        out.add(Section("Favoris", favs.take(FAV_PREVIEW), id = FAV_CAT, seeAll = FAV_CAT))
                    }
                    if (recent.isNotEmpty()) {
                        out.add(Section("Nouveautés", recent.take(NEW_PREVIEW), id = NEW_CAT, seeAll = NEW_CAT, showDates = true))
                    }
                    val byCat = items.groupBy { it.categoryId }
                    categories.forEach { c -> byCat[c.id]?.let { out.add(Section(c.name, it, id = "cat:" + c.id)) } }
                    val known = categories.mapTo(HashSet()) { it.id }
                    val others = items.filter { it.categoryId !in known }
                    if (others.isNotEmpty()) out.add(Section("Autres", others, id = OTHERS_ID))
                    out
                }
            }
        }
    }

    val gridState = rememberLazyGridState()

    // Blocs repliés de la vue « Toutes » (#15) : toucher le titre d'une catégorie la replie / déplie.
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
    val canCollapse = query.isBlank() && cat == null && type != ContentType.LIVE && allSectionIds.isNotEmpty()
    val allCollapsed = canCollapse && allSectionIds.all { it in collapsed }

    // Remonte en haut de la liste quand on change de catégorie ou de recherche.
    LaunchedEffect(cat, query) {
        runCatching { listState.scrollToItem(0) }
        runCatching { gridState.scrollToItem(0) }
    }

    Column(Modifier.fillMaxSize()) {
        // Recherche et catégorie sur une seule ligne compacte (40 dp) : accessibles mais discrètes.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Rechercher…",
                modifier = Modifier.weight(1f),
            )
            if (query.isBlank() && type != ContentType.LIVE) {
                CategoryDropdown(
                    selected = cat,
                    categories = categories,
                    counts = catCounts,
                    watchingCount = watching.size,
                    favCount = favs.size,
                    recentCount = recent.size,
                    onSelect = { cat = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // TV : rangée de boutons de catégorie (films et séries ont la liste déroulante ci-dessus).
        if (query.isBlank() && type == ContentType.LIVE) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("Toutes") }) }
                if (watching.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = cat == WATCH_CAT,
                            onClick = { cat = WATCH_CAT },
                            label = { Text("En cours") },
                            leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp)) },
                        )
                    }
                }
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
        val total = if (cat == WATCH_CAT) watching.size else list?.filter { it.seeAll == null }?.sumOf { it.items.size } ?: 0
        Row(
            Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(start = 14.dp, end = 4.dp),
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
                    NEW_CAT -> "Aucune nouveauté pour le moment."
                    FAV_CAT -> "Aucun favori pour l'instant.\n\nTouchez le petit cœur en haut à droite d'une affiche, " +
                        "ou celui de la fiche d'un film ou d'une série."
                    else -> "Aucun résultat"
                }
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
                        ) {
                            if (type == ContentType.SERIES) onOpenSeries(m) else selectedMovie = m
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
            onRemove = { watchMenu = null; vm.removeWatching(e.meta.key) },
            onDismiss = { watchMenu = null },
        )
    }
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
fun WatchCard(e: WatchEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            Poster(e.meta.icon, e.meta.title, Modifier.fillMaxWidth())
            LinearProgressIndicator(
                progress = { e.progress },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp),
                trackColor = Color.Black.copy(alpha = 0.55f),
            )
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
                FavoriteButton(vm.isFavorite(m)) { toggleFavoriteWithToast(ctx, vm, m) }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Row {
                    Poster(m.icon, m.name, Modifier.width(90.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
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
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onToggleFavorite)) {
        Box {
            Poster(m.icon, m.name, Modifier.fillMaxWidth())
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


/** Choix de la catégorie (films et séries) : pastille compacte qui ouvre une liste déroulante. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: String?,
    categories: List<Category>,
    counts: Map<String, Int>,
    watchingCount: Int,
    favCount: Int,
    recentCount: Int,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        null -> "Toutes"
        WATCH_CAT -> "En cours"
        FAV_CAT -> "Favoris"
        NEW_CAT -> "Nouveautés"
        else -> categories.firstOrNull { it.id == selected }?.name ?: "Toutes"
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.FilterList, "Catégorie", Modifier.size(18.dp),
                tint = if (selected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchTextFieldWidth = false,
            modifier = Modifier.width(280.dp),
        ) {
            @Composable
            fun option(id: String?, text: String, count: Int?, icon: ImageVector? = null) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (id == selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (id == selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                modifier = Modifier.weight(1f),
                            )
                            if (count != null) {
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    },
                    leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
                    onClick = { onSelect(id); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            option(null, "Toutes les catégories", null)
            if (watchingCount > 0) option(WATCH_CAT, "En cours", watchingCount, Icons.Filled.History)
            option(FAV_CAT, "Favoris", favCount, Icons.Filled.Favorite)
            if (recentCount > 0) option(NEW_CAT, "Nouveautés", recentCount, Icons.Filled.NewReleases)
            HorizontalDivider()
            categories.forEach { c -> option(c.id, c.name, counts[c.id] ?: 0) }
        }
    }
}

/** Cœur plein (favori) ou vide, pour ajouter / retirer un titre des favoris. */
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
