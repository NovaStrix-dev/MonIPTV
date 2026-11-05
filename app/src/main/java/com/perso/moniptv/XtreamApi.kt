package com.perso.moniptv

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Lecture d'une valeur JSON en ignorant les null / chaînes vides. */
fun JSONObject.str(key: String): String? {
    val v = opt(key)
    if (v == null || v == JSONObject.NULL) return null
    val s = v.toString().trim()
    return if (s.isEmpty() || s == "null") null else s
}

class XtreamApi(private val acc: Account) {

    private val base = acc.server.trimEnd('/')
    private val qUser = URLEncoder.encode(acc.username, "UTF-8")
    private val qPass = URLEncoder.encode(acc.password, "UTF-8")
    private val pathAuth = "${acc.username}/${acc.password}"

    private fun api(action: String?, extra: String = ""): String {
        val sb = StringBuilder("$base/player_api.php?username=$qUser&password=$qPass")
        if (action != null) sb.append("&action=").append(action)
        sb.append(extra)
        return Http.get(sb.toString())
    }

    private fun objects(json: String): List<JSONObject> {
        val t = json.trim()
        if (!t.startsWith("[")) return emptyList()
        val a = JSONArray(t)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    /** Vérifie les identifiants et renvoie l'extension à utiliser pour le direct. */
    fun authenticate(): String {
        val raw = api(null).trim()
        if (!raw.startsWith("{")) throw IOException("Réponse inattendue : vérifiez l'adresse du serveur")
        val js = JSONObject(raw)
        val ui = js.optJSONObject("user_info") ?: throw IOException("Réponse invalide du serveur")
        if (ui.optInt("auth", 0) != 1) throw IOException("Identifiant ou mot de passe refusé")
        val status = ui.str("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Abonnement non actif ($status)")
        }
        val formats = ui.optJSONArray("allowed_output_formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                if (formats.optString(i) == "m3u8") return "m3u8"
            }
            if (formats.length() > 0) return "ts"
        }
        return "m3u8"
    }

    fun categories(action: String): List<Category> = objects(api(action)).mapNotNull { o ->
        val id = o.str("category_id") ?: return@mapNotNull null
        Category(id, o.str("category_name") ?: id)
    }

    fun live(ext: String): List<Media> = objects(api("get_live_streams")).mapNotNull { o ->
        val id = o.str("stream_id") ?: return@mapNotNull null
        Media(
            id = id,
            name = o.str("name") ?: "Chaîne $id",
            type = ContentType.LIVE,
            categoryId = o.str("category_id") ?: "",
            icon = o.str("stream_icon"),
            url = "$base/live/$pathAuth/$id.$ext",
            ext = ext,
        )
    }

    fun movies(): List<Media> = objects(api("get_vod_streams")).mapNotNull { o ->
        val id = o.str("stream_id") ?: return@mapNotNull null
        val ext = o.str("container_extension") ?: "mp4"
        Media(
            id = id,
            name = o.str("name") ?: "Film $id",
            type = ContentType.MOVIE,
            categoryId = o.str("category_id") ?: "",
            icon = o.str("stream_icon"),
            url = "$base/movie/$pathAuth/$id.$ext",
            ext = ext,
            added = o.str("added")?.toLongOrNull() ?: 0,
        )
    }

    fun series(): List<Media> = objects(api("get_series")).mapNotNull { o ->
        val id = o.str("series_id") ?: return@mapNotNull null
        Media(
            id = id,
            name = o.str("name") ?: "Série $id",
            type = ContentType.SERIES,
            categoryId = o.str("category_id") ?: "",
            icon = o.str("cover"),
            url = null,
            ext = null,
            added = o.str("last_modified")?.toLongOrNull() ?: 0,
        )
    }

    /** Fiche d'un film : résumé, année, genre, durée, note. */
    fun movieDetails(vodId: String): MediaDetails? {
        val raw = api("get_vod_info", "&vod_id=$vodId").trim()
        if (!raw.startsWith("{")) return null
        val info = JSONObject(raw).optJSONObject("info") ?: return null
        return details(info, duration = info.str("duration")?.let { formatDuration(it) })
    }

    private fun details(info: JSONObject, duration: String? = null): MediaDetails? {
        val plot = info.str("plot") ?: info.str("description")
        val year = (info.str("releasedate") ?: info.str("releaseDate") ?: info.str("release_date"))
            ?.let { Regex("(19|20)\\d{2}").find(it)?.value }
            ?: info.str("year")
        val rating = info.str("rating")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.US, it) }
        val d = MediaDetails(
            plot = plot,
            year = year,
            genre = info.str("genre"),
            duration = duration,
            rating = rating,
        )
        return if (d.isEmpty) null else d
    }

    /** « 01:52:10 » → « 1 h 52 » ; « 45:00 » → « 45 min ». */
    private fun formatDuration(s: String): String? {
        val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        val minutes = when (parts.size) {
            3 -> parts[0] * 60 + parts[1]
            2 -> parts[0]
            else -> return null
        }
        if (minutes <= 0) return null
        return if (minutes >= 60) "${minutes / 60} h %02d".format(minutes % 60) else "$minutes min"
    }

    fun seriesInfo(seriesId: String): SeriesInfo {
        val raw = api("get_series_info", "&series_id=$seriesId").trim()
        if (!raw.startsWith("{")) return SeriesInfo(null, emptyList())
        val js = JSONObject(raw)
        val det = js.optJSONObject("info")?.let { details(it) }
        val found = mutableListOf<JSONObject>()
        fun collect(a: JSONArray) {
            for (i in 0 until a.length()) {
                when (val x = a.opt(i)) {
                    is JSONObject -> found.add(x)
                    is JSONArray -> collect(x)
                }
            }
        }
        when (val raw = js.opt("episodes")) {
            is JSONObject -> raw.keys().forEach { k -> raw.optJSONArray(k)?.let { collect(it) } }
            is JSONArray -> collect(raw)
        }
        val eps = found.mapNotNull { o ->
            val id = o.str("id") ?: return@mapNotNull null
            val ext = o.str("container_extension") ?: "mp4"
            val num = o.optInt("episode_num", 0)
            Episode(
                id = id,
                title = o.str("title") ?: "Épisode $num",
                season = o.optInt("season", 1),
                number = num,
                url = "$base/series/$pathAuth/$id.$ext",
                ext = ext,
                icon = o.optJSONObject("info")?.str("movie_image"),
            )
        }.sortedWith(compareBy({ it.season }, { it.number }))
        return SeriesInfo(det, eps)
    }
}
