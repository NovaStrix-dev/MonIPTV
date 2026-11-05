package com.perso.moniptv

import java.text.Normalizer

enum class AccountType { XTREAM, M3U }

data class Account(
    val type: AccountType,
    val server: String,
    val username: String,
    val password: String,
)

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

data class Media(
    val id: String,
    val name: String,
    val type: ContentType,
    val categoryId: String,
    val icon: String?,
    val url: String?,
    val ext: String?,
    /** Date d'ajout (secondes depuis 1970), 0 si inconnue. */
    val added: Long = 0,
) {
    val searchKey: String by lazy { normalizeText(name) }
    /** Identifiant stable d'un rafraîchissement à l'autre. */
    val stableKey: String get() = url ?: id
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val ext: String,
    val icon: String?,
)

data class Catalog(
    val liveCats: List<Category>,
    val live: List<Media>,
    val movieCats: List<Category>,
    val movies: List<Media>,
    val seriesCats: List<Category>,
    val series: List<Media>,
    /** Épisodes des séries issues d'une playlist M3U (clé = id de la série). */
    val m3uEpisodes: Map<String, List<Episode>> = emptyMap(),
    /** Heure de la dernière mise à jour (millisecondes). */
    val updatedAt: Long = 0,
)

private val diacritics = Regex("\\p{Mn}+")

fun normalizeText(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

private val wordSplit = Regex("[^\\p{L}\\p{N}]+")

/** Découpe la recherche en mots (sans accents ni majuscules). */
fun queryWords(q: String): List<String> = normalizeText(q).split(wordSplit).filter { it.isNotEmpty() }

/** Vrai si chaque mot recherché apparaît dans le titre (n'importe où, même au milieu). */
fun Media.matches(words: List<String>): Boolean = words.all { searchKey.contains(it) }
