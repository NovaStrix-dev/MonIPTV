package com.perso.moniptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Garde le catalogue sur l'appareil pour un démarrage instantané (et hors ligne). */
object CatalogCache {

    private fun file(ctx: Context) = File(ctx.filesDir, "catalog.json")

    fun delete(ctx: Context) {
        file(ctx).delete()
    }

    private fun cats(list: List<Category>) = JSONArray().apply {
        list.forEach { put(JSONArray().put(it.id).put(it.name)) }
    }

    private fun medias(list: List<Media>) = JSONArray().apply {
        list.forEach {
            put(
                JSONArray().put(it.id).put(it.name).put(it.categoryId)
                    .put(it.icon ?: "").put(it.url ?: "").put(it.ext ?: "").put(it.added)
            )
        }
    }

    fun write(ctx: Context, c: Catalog) {
        val eps = JSONObject()
        c.m3uEpisodes.forEach { (key, list) ->
            eps.put(key, JSONArray().apply {
                list.forEach { e ->
                    put(
                        JSONArray().put(e.id).put(e.title).put(e.season).put(e.number)
                            .put(e.url).put(e.ext).put(e.icon ?: "")
                    )
                }
            })
        }
        val o = JSONObject()
            .put("liveCats", cats(c.liveCats)).put("live", medias(c.live))
            .put("movieCats", cats(c.movieCats)).put("movies", medias(c.movies))
            .put("seriesCats", cats(c.seriesCats)).put("series", medias(c.series))
            .put("eps", eps)
            .put("updatedAt", c.updatedAt)
        val tmp = File(ctx.filesDir, "catalog.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file(ctx))
    }

    fun read(ctx: Context): Catalog? {
        val f = file(ctx)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            fun readCats(k: String): List<Category> {
                val a = o.getJSONArray(k)
                return (0 until a.length()).map { val x = a.getJSONArray(it); Category(x.getString(0), x.getString(1)) }
            }
            fun readMedia(k: String, t: ContentType): List<Media> {
                val a = o.getJSONArray(k)
                return (0 until a.length()).map {
                    val x = a.getJSONArray(it)
                    Media(
                        x.getString(0), x.getString(1), t, x.getString(2),
                        x.getString(3).ifEmpty { null }, x.getString(4).ifEmpty { null },
                        x.getString(5).ifEmpty { null },
                        x.optLong(6, 0L),
                    )
                }
            }
            val epsObj = o.optJSONObject("eps") ?: JSONObject()
            val eps = HashMap<String, List<Episode>>()
            epsObj.keys().forEach { key ->
                val a = epsObj.getJSONArray(key)
                eps[key] = (0 until a.length()).map {
                    val x = a.getJSONArray(it)
                    Episode(
                        x.getString(0), x.getString(1), x.getInt(2), x.getInt(3),
                        x.getString(4), x.getString(5), x.getString(6).ifEmpty { null },
                    )
                }
            }
            Catalog(
                readCats("liveCats"), readMedia("live", ContentType.LIVE),
                readCats("movieCats"), readMedia("movies", ContentType.MOVIE),
                readCats("seriesCats"), readMedia("series", ContentType.SERIES),
                eps,
                o.optLong("updatedAt", 0L),
            )
        } catch (e: Exception) {
            null
        }
    }
}
