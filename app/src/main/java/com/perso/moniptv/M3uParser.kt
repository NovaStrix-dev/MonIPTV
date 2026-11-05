package com.perso.moniptv

/**
 * Analyse une playlist M3U / M3U_plus et répartit les entrées
 * en TV en direct, films et séries (épisodes regroupés par série).
 */
object M3uParser {

    private val attrRe = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val episodeRe = Regex("^(.*?)[\\s._-]*[Ss](\\d{1,2})[\\s._-]*[Ee](\\d{1,3})")
    private val vodExt = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm")

    fun parse(lines: Sequence<String>): Catalog {
        val live = ArrayList<Media>()
        val movies = ArrayList<Media>()
        val seriesMeta = LinkedHashMap<String, Media>()
        val seriesEps = LinkedHashMap<String, MutableList<Episode>>()
        val liveCats = LinkedHashMap<String, Category>()
        val movieCats = LinkedHashMap<String, Category>()
        val seriesCats = LinkedHashMap<String, Category>()

        var name: String? = null
        var attrs: Map<String, String> = emptyMap()
        var counter = 0

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                attrs = attrRe.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                val lastQuote = line.lastIndexOf('"')
                val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                name = if (comma >= 0) line.substring(comma + 1).trim() else null
                continue
            }
            if (line.startsWith("#")) continue

            // Ligne d'URL
            val url = line
            counter++
            val title = (name?.takeIf { it.isNotBlank() } ?: attrs["tvg-name"]
                ?: url.substringAfterLast('/')).trim()
            val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Sans catégorie"
            val logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
            val ext = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
                .lowercase().takeIf { it.length in 2..4 }
            val lowerUrl = url.lowercase()
            val epMatch = episodeRe.find(title)

            val type = when {
                "/series/" in lowerUrl -> ContentType.SERIES
                "/movie/" in lowerUrl -> ContentType.MOVIE
                ext != null && ext in vodExt -> if (epMatch != null) ContentType.SERIES else ContentType.MOVIE
                else -> ContentType.LIVE
            }

            when (type) {
                ContentType.LIVE -> {
                    liveCats.getOrPut(group) { Category(group, group) }
                    live.add(Media("l$counter", title, type, group, logo, url, ext ?: "ts"))
                }
                ContentType.MOVIE -> {
                    movieCats.getOrPut(group) { Category(group, group) }
                    movies.add(Media("m$counter", title, type, group, logo, url, ext ?: "mp4"))
                }
                ContentType.SERIES -> {
                    seriesCats.getOrPut(group) { Category(group, group) }
                    val show = epMatch?.groupValues?.get(1)?.trim()?.trimEnd('-', '.', ' ')
                        ?.takeIf { it.isNotBlank() } ?: title
                    val key = "s:" + normalizeText(show)
                    seriesMeta.getOrPut(key) {
                        Media(key, show, ContentType.SERIES, group, logo, null, null)
                    }
                    val list = seriesEps.getOrPut(key) { mutableListOf() }
                    val season = epMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1
                    val number = epMatch?.groupValues?.get(3)?.toIntOrNull() ?: (list.size + 1)
                    list.add(Episode("e$counter", title, season, number, url, ext ?: "mp4", logo))
                }
            }
            name = null
            attrs = emptyMap()
        }

        return Catalog(
            liveCats = liveCats.values.toList(),
            live = live,
            movieCats = movieCats.values.toList(),
            movies = movies,
            seriesCats = seriesCats.values.toList(),
            series = seriesMeta.values.toList(),
            m3uEpisodes = seriesEps.mapValues { (_, v) -> v.sortedWith(compareBy({ it.season }, { it.number })) },
        )
    }
}
