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
    val searchKey: String by lazy { searchText(name) }
    /** Titre sans espaces ni ponctuation : « S.W.A.T » ou « Spider‑Man » se trouvent aussi (#29). */
    val compactKey: String by lazy { searchKey.replace(nonWord, "") }
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

/** Fiche détaillée d'un film ou d'une série (Xtream uniquement, chargée à l'ouverture). */
data class MediaDetails(
    val plot: String?,
    val year: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val rating: String? = null,
) {
    /** « 2021 · Action, Aventure · 1 h 52 · ★ 7.4 » */
    val facts: String get() = listOfNotNull(year, genre, duration, rating?.let { "★ $it" }).joinToString(" · ")
    val isEmpty: Boolean get() = plot.isNullOrBlank() && facts.isEmpty()
}

/** Épisodes d'une série et sa fiche (résumé), obtenus en une seule requête. */
data class SeriesInfo(val details: MediaDetails?, val episodes: List<Episode>)

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
private val nonWord = wordSplit
/** Accents, signes combinants et caractères invisibles (espace sans largeur, trait d'union conditionnel…). */
private val searchStrip = Regex("[\\p{Mn}\\p{Me}\\p{Cf}]+")

/**
 * Forme de recherche d'un texte (#29) : comme [normalizeText], mais en forme de compatibilité (NFKD),
 * qui ramène les lettres décoratives (𝐀, Ａ, ᴬ…) et les ligatures (ﬁ) aux lettres ordinaires, et sans les
 * caractères invisibles que certains fournisseurs glissent dans les titres.
 * Réservée à la recherche : [normalizeText] reste inchangée car elle sert aussi à former des identifiants.
 */
fun searchText(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKD).replace(searchStrip, "").lowercase()

/** Découpe la recherche en mots (sans accents ni majuscules). */
fun queryWords(q: String): List<String> = searchText(q).split(wordSplit).filter { it.isNotEmpty() }

/** Vrai si chaque mot recherché apparaît dans le titre (n'importe où, même au milieu). */
fun Media.matches(words: List<String>): Boolean = words.all { searchKey.contains(it) || compactKey.contains(it) }

/** Tri du dernier ajouté au premier ; les titres sans date gardent leur ordre, après les titres datés. */
fun List<Media>.newestFirst(): List<Media> = sortedByDescending { it.added }

/** Nom de la catégorie d'un titre (films, séries ou chaînes), null si inconnue (#22). */
fun Catalog.categoryName(m: Media): String? {
    val cats = when (m.type) {
        ContentType.LIVE -> liveCats
        ContentType.MOVIE -> movieCats
        ContentType.SERIES -> seriesCats
    }
    return cats.firstOrNull { it.id == m.categoryId }?.name?.takeIf { it.isNotBlank() }
}
