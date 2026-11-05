package com.perso.moniptv

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DownloadRecord(
    val downloadId: Long,
    val key: String,
    val title: String,
    val icon: String?,
    val fileName: String,
)

enum class DlState { PENDING, RUNNING, PAUSED, DONE, FAILED }

data class DownloadInfo(
    val record: DownloadRecord,
    val state: DlState,
    val progress: Float,
    val file: File,
)

/**
 * Téléchargements via le gestionnaire Android (continue en arrière-plan,
 * notification, reprise après coupure réseau). Les fichiers restent privés à l'appli.
 */
class Downloads(private val ctx: Context) {

    private val dm = ctx.getSystemService(DownloadManager::class.java)
    private val prefs = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    private fun dir(): File = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(ctx.filesDir, "movies")

    fun fileOf(r: DownloadRecord) = File(dir(), r.fileName)

    @Synchronized
    fun records(): List<DownloadRecord> {
        val a = JSONArray(prefs.getString("list", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            DownloadRecord(
                o.getLong("id"), o.getString("key"), o.getString("title"),
                o.optString("icon").ifEmpty { null }, o.getString("file"),
            )
        }
    }

    @Synchronized
    private fun saveRecords(list: List<DownloadRecord>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.downloadId).put("key", it.key).put("title", it.title)
                    .put("icon", it.icon ?: "").put("file", it.fileName)
            )
        }
        prefs.edit().putString("list", a.toString()).apply()
    }

    fun isDownloaded(key: String) = records().any { it.key == key }

    /** @return false si déjà présent dans les téléchargements. */
    fun start(key: String, title: String, icon: String?, url: String, ext: String): Boolean {
        if (isDownloaded(key)) return false
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).trim()
        val fileName = "${safe}_${System.currentTimeMillis() % 1_000_000}.$ext"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("MonIPTV")
            .addRequestHeader("User-Agent", Http.USER_AGENT)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_MOVIES, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val id = dm.enqueue(req)
        saveRecords(records() + DownloadRecord(id, key, title, icon, fileName))
        return true
    }

    fun list(): List<DownloadInfo> {
        val recs = records()
        if (recs.isEmpty()) return emptyList()
        val status = HashMap<Long, Triple<Int, Long, Long>>()
        dm.query(DownloadManager.Query().setFilterById(*recs.map { it.downloadId }.toLongArray()))?.use { c ->
            val iId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val iSt = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val iDone = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val iTotal = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (c.moveToNext()) {
                status[c.getLong(iId)] = Triple(c.getInt(iSt), c.getLong(iDone), c.getLong(iTotal))
            }
        }
        return recs.reversed().map { r ->
            val f = fileOf(r)
            val s = status[r.downloadId]
            if (s == null) {
                DownloadInfo(r, if (f.exists() && f.length() > 0) DlState.DONE else DlState.FAILED, 1f, f)
            } else {
                val (st, done, total) = s
                val state = when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> DlState.DONE
                    DownloadManager.STATUS_RUNNING -> DlState.RUNNING
                    DownloadManager.STATUS_PAUSED -> DlState.PAUSED
                    DownloadManager.STATUS_FAILED -> DlState.FAILED
                    else -> DlState.PENDING
                }
                val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                DownloadInfo(r, state, p, f)
            }
        }
    }

    fun delete(r: DownloadRecord) {
        runCatching { dm.remove(r.downloadId) }
        fileOf(r).delete()
        saveRecords(records().filter { it.downloadId != r.downloadId })
    }
}
