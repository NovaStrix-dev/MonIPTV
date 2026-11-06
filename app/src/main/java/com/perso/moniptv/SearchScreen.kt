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

private data class Results(val movies: List<Media>, val series: List<Media>)

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

    val results by produceState(initialValue = Results(emptyList(), emptyList()), query, catalog) {
        delay(250) // attend la fin de la frappe
        value = withContext(Dispatchers.Default) {
            val words = queryWords(query)
            if (words.isEmpty()) Results(emptyList(), emptyList())
            else Results(
                catalog.movies.filter { it.matches(words) }.newestFirst(),
                catalog.series.filter { it.matches(words) }.newestFirst(),
            )
        }
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

        val total = (if (showMovies) results.movies.size else 0) + (if (showSeries) results.series.size else 0)
        when {
            queryWords(query).isEmpty() -> CenterMessage("Tapez un mot contenu dans le titre d'un film ou d'une série.")
            total == 0 -> CenterMessage("Aucun film ni série ne contient « ${query.trim()} » dans son titre.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(POSTER_MIN_WIDTH),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showMovies && results.movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Films (${results.movies.size})") }
                    items(results.movies) { m -> PosterCard(m, movieCats[m.categoryId], vm.isFavorite(m), onToggleFavorite = { toggleFavoriteWithToast(ctx, vm, m) }, watched = vm.isWatched(m)) { selectedMovie = m } }
                }
                if (showSeries && results.series.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Séries (${results.series.size})") }
                    items(results.series) { m -> PosterCard(m, seriesCats[m.categoryId], vm.isFavorite(m), onToggleFavorite = { toggleFavoriteWithToast(ctx, vm, m) }, watched = vm.isWatched(m)) { onOpenSeries(m) } }
                }
            }
        }
    }

    selectedMovie?.let { m -> MovieDialog(vm, m) { selectedMovie = null } }
}

