package com.perso.moniptv

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File

/**
 * Garde le catalogue sur l'appareil pour un démarrage instantané (et hors ligne).
 *
 * Écriture et lecture « au fil de l'eau » (JsonWriter / JsonReader) : le fichier n'est jamais
 * entièrement chargé en mémoire. Avec un gros catalogue de films, l'ancienne version (un seul
 * JSONObject géant) dépassait la mémoire permise sur un boîtier Android TV (#29).
 * Le format du fichier ne change pas : les anciens caches restent lisibles.
 */
object CatalogCache {

    private fun file(ctx: Context) = File(ctx.filesDir, "catalog.json")

    fun delete(ctx: Context) {
        file(ctx).delete()
    }

    private fun JsonWriter.cats(key: String, list: List<Category>) {
        name(key).beginArray()
        list.forEach { beginArray().value(it.id).value(it.name).endArray() }
        endArray()
    }

    private fun JsonWriter.medias(key: String, list: List<Media>) {
        name(key).beginArray()
        list.forEach {
            beginArray()
                .value(it.id).value(it.name).value(it.categoryId)
                .value(it.icon ?: "").value(it.url ?: "").value(it.ext ?: "").value(it.added)
                .endArray()
        }
        endArray()
    }

    fun write(ctx: Context, c: Catalog) {
        val tmp = File(ctx.filesDir, "catalog.tmp")
        JsonWriter(tmp.bufferedWriter()).use { w ->
            w.beginObject()
            w.cats("liveCats", c.liveCats); w.medias("live", c.live)
            w.cats("movieCats", c.movieCats); w.medias("movies", c.movies)
            w.cats("seriesCats", c.seriesCats); w.medias("series", c.series)
            w.name("eps").beginObject()
            c.m3uEpisodes.forEach { (key, list) ->
                w.name(key).beginArray()
                list.forEach { e ->
                    w.beginArray()
                        .value(e.id).value(e.title).value(e.season.toLong()).value(e.number.toLong())
                        .value(e.url).value(e.ext).value(e.icon ?: "")
                        .endArray()
                }
                w.endArray()
            }
            w.endObject()
            w.name("updatedAt").value(c.updatedAt)
            w.endObject()
        }
        tmp.renameTo(file(ctx))
    }

    /** Lit une ligne de tableau JSON en chaînes (nombres convertis, null → ""). */
    private fun JsonReader.row(): List<String> {
        val out = ArrayList<String>(8)
        beginArray()
        while (hasNext()) {
            out += when (peek()) {
                JsonToken.NULL -> { nextNull(); "" }
                JsonToken.BOOLEAN -> nextBoolean().toString()
                JsonToken.STRING, JsonToken.NUMBER -> nextString()
                else -> { skipValue(); "" }
            }
        }
        endArray()
        return out
    }

    private fun JsonReader.rows(): List<List<String>> {
        val out = ArrayList<List<String>>()
        beginArray()
        while (hasNext()) out += row()
        endArray()
        return out
    }

    private fun JsonReader.cats(): List<Category> = rows().map { Category(it[0], it[1]) }

    private fun JsonReader.medias(t: ContentType): List<Media> {
        val out = ArrayList<Media>()
        beginArray()
        while (hasNext()) {
            val x = row()
            out += Media(
                x[0], x[1], t, x[2],
                x[3].ifEmpty { null }, x[4].ifEmpty { null }, x[5].ifEmpty { null },
                x.getOrNull(6)?.toLongOrNull() ?: 0L,
            )
        }
        endArray()
        return out
    }

    fun read(ctx: Context): Catalog? {
        val f = file(ctx)
        if (!f.exists()) return null
        return try {
            var liveCats = emptyList<Category>(); var live = emptyList<Media>()
            var movieCats = emptyList<Category>(); var movies = emptyList<Media>()
            var seriesCats = emptyList<Category>(); var series = emptyList<Media>()
            val eps = HashMap<String, List<Episode>>()
            var updatedAt = 0L
            JsonReader(f.bufferedReader()).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "liveCats" -> liveCats = r.cats()
                        "live" -> live = r.medias(ContentType.LIVE)
                        "movieCats" -> movieCats = r.cats()
                        "movies" -> movies = r.medias(ContentType.MOVIE)
                        "seriesCats" -> seriesCats = r.cats()
                        "series" -> series = r.medias(ContentType.SERIES)
                        "eps" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                val key = r.nextName()
                                eps[key] = r.rows().map { x ->
                                    Episode(
                                        x[0], x[1], x[2].toInt(), x[3].toInt(),
                                        x[4], x[5], x[6].ifEmpty { null },
                                    )
                                }
                            }
                            r.endObject()
                        }
                        "updatedAt" -> updatedAt = r.nextString().toLongOrNull() ?: 0L
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            Catalog(liveCats, live, movieCats, movies, seriesCats, series, eps, updatedAt)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
