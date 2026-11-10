package com.perso.moniptv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class Scope(val label: String) { ALL("Films et séries"), MOVIES("Films"), SERIES("Séries") }

/** Résultats d'une recherche, avec les mots qui les ont produits (pour savoir s'ils sont à jour). */
private data class Results(val words: List<String>, val movies: List<Media>, val series: List<Media>)

/**
 * Recherche par mot dans les titres de tous les films et toutes les séries.
 * Ex. « nuit » trouve « La Nuit au musée » ; plusieurs mots = tous doivent être dans le titre.
 */
@Composable
fun SearchScreen(vm: AppViewModel, catalog: Catalog, onOpenSeries: (Media) -> Unit) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(Scope.ALL) }
    var selectedMovie by remember { mutableStateOf<Media?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // null tant que la première recherche n'est pas calculée (ex. au retour d'une série) :
    // évite d'afficher « Aucun film ni série » pendant le calcul.
    val results by produceState<Results?>(initialValue = null, query, catalog) {
        delay(250) // attend la fin de la frappe
        value = withContext(Dispatchers.Default) {
            val words = queryWords(query)
            if (words.isEmpty()) Results(words, emptyList(), emptyList())
            else Results(
                words,
                catalog.movies.filter { it.matches(words) }.newestFirst(),
                catalog.series.filter { it.matches(words) }.newestFirst(),
            )
        }
    }

    // Position de la grille, gardée au retour d'une série (#26) mais remise en haut à chaque
    // nouvelle recherche ou nouveau filtre. Sans cela, la grille restait à l'ancienne position :
    // les films étant en premier, on tombait sur la fin de la liste, c'est-à-dire sur les séries.
    val gridState = rememberLazyGridState()
    var shownSearch by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(results, scope) {
        val r = results ?: return@LaunchedEffect
        val key = r.words.joinToString(" ") + "|" + scope.name
        if (key != shownSearch) {
            shownSearch = key
            runCatching { gridState.scrollToItem(0) }
        }
    }

    // #29 : une recherche sans aucun film est notée dans le journal de diagnostic (icône insecte),
    // avec la taille du catalogue et quelques titres de films, pour comprendre la cause à distance.
    var loggedSearch by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(results) {
        val r = results ?: return@LaunchedEffect
        val key = r.words.joinToString(" ")
        if (r.words.isEmpty() || r.movies.isNotEmpty() || key == loggedSearch) return@LaunchedEffect
        delay(1500) // seulement quand la frappe est finie
        loggedSearch = key
        // De préférence des titres contenant des lettres non latines (sosies cyrilliques ou grecs…).
        val odd = catalog.movies.asSequence().filter { m -> m.name.any { it.code >= 0x250 } }.take(3).toList()
        val sample = odd.ifEmpty { catalog.movies.take(3) }.joinToString(" | ") { visibleChars(it.name) }
        Diagnostics.log(
            ctx,
            "recherche « $key » : 0 film sur ${catalog.movies.size}, ${r.series.size} séries sur ${catalog.series.size}" +
                if (sample.isEmpty()) "" else " · ex. films : $sample",
        )
    }

    val movieCats = remember(catalog) { catalog.movieCats.associate { it.id to it.name } }
    val seriesCats = remember(catalog) { catalog.seriesCats.associate { it.id to it.name } }
    val showMovies = scope != Scope.SERIES
    val showSeries = scope != Scope.MOVIES

    Column(Modifier.fillMaxSize()) {
        CompactSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Un mot du titre…",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            fieldModifier = Modifier.focusRequester(focus),
            onSearch = { keyboard?.hide() },
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Scope.entries.forEach { s ->
                FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) })
            }
        }

        val r = results
        val words = queryWords(query)
        val total = if (r == null) 0 else (if (showMovies) r.movies.size else 0) + (if (showSeries) r.series.size else 0)
        when {
            words.isEmpty() -> CenterMessage("Tapez un mot contenu dans le titre d'un film ou d'une série.")
            r == null -> CenterMessage("Recherche…", loading = true)
            total == 0 && r.words == words -> CenterMessage(
                "Aucun film ni série ne contient « ${query.trim()} » dans son titre.\n\n" +
                    "Catalogue : ${catalog.movies.size} films, ${catalog.series.size} séries.",
            )
            total == 0 -> CenterMessage("Recherche…", loading = true)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(POSTER_MIN_WIDTH),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showMovies && r.movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Films (${r.movies.size})") }
                    items(r.movies) { m -> PosterCard(m, movieCats[m.categoryId], vm.isFavorite(m), onToggleFavorite = { toggleFavoriteWithToast(ctx, vm, m) }, watched = vm.isWatched(m)) { selectedMovie = m } }
                }
                if (showSeries && r.series.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Séries (${r.series.size})") }
                    items(r.series) { m -> PosterCard(m, seriesCats[m.categoryId], vm.isFavorite(m), onToggleFavorite = { toggleFavoriteWithToast(ctx, vm, m) }, watched = vm.isWatched(m)) { onOpenSeries(m) } }
                }
            }
        }
    }

    selectedMovie?.let { m -> MovieDialog(vm, m) { selectedMovie = null } }
}


/** Titre tel quel, avec les caractères inhabituels écrits en code (U+200B…) pour le journal. */
private fun visibleChars(s: String): String = buildString {
    s.take(40).forEach { c ->
        if (c.code in 0x20..0x7E || (Character.isLetter(c) && c.code < 0x250)) append(c)
        else append("<U+%04X>".format(c.code))
    }
}
