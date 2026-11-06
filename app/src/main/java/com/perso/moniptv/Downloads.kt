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

enum class DlState { QUEUED, PENDING, RUNNING, PAUSED, DONE, FAILED }

/** Épisode en file d'attente : il sera confié au gestionnaire Android quand le précédent sera fini. */
data class QueuedDownload(
    val key: String,
    val title: String,
    val icon: String?,
    val url: String,
    val ext: String,
)

data class DownloadInfo(
    val record: DownloadRecord,
    val state: DlState,
    val progress: Float,
    val file: File,
    /** Position dans la file d'attente (1 = le prochain), pour l'état QUEUED. */
    val queuePos: Int = 0,
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

    // ---------- File d'attente (#16 : saison complète) ----------
    // Beaucoup de fournisseurs n'acceptent qu'une connexion à la fois : les épisodes mis en file
    // sont téléchargés l'un après l'autre. pump() lance le suivant quand plus rien n'est en cours ;
    // il est appelé à l'ajout, à la fin de chaque téléchargement (DownloadDoneReceiver) et par l'écran Hors ligne.

    @Synchronized
    fun queue(): List<QueuedDownload> {
        val a = JSONArray(prefs.getString("queue", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            QueuedDownload(
                o.getString("key"), o.getString("title"), o.optString("icon").ifEmpty { null },
                o.getString("url"), o.getString("ext"),
            )
        }
    }

    @Synchronized
    private fun saveQueue(list: List<QueuedDownload>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("key", it.key).put("title", it.title).put("icon", it.icon ?: "")
                    .put("url", it.url).put("ext", it.ext)
            )
        }
        prefs.edit().putString("queue", a.toString()).apply()
    }

    fun isQueued(key: String) = queue().any { it.key == key }

    /** Téléchargé, en cours ou en file d'attente. */
    fun isKnown(key: String) = isDownloaded(key) || isQueued(key)

    /** Ajoute des épisodes à la file (ceux déjà connus sont ignorés). @return le nombre ajouté. */
    fun enqueue(items: List<QueuedDownload>): Int = synchronized(LOCK) {
        val known = records().mapTo(HashSet()) { it.key } + queue().map { it.key }
        val fresh = items.filter { it.key !in known }.distinctBy { it.key }
        if (fresh.isNotEmpty()) saveQueue(queue() + fresh)
        pump()
        fresh.size
    }

    /** Vrai si un téléchargement de l'appli est en attente, en cours ou en pause chez le gestionnaire Android. */
    private fun anyActive(): Boolean {
        val recs = records()
        if (recs.isEmpty()) return false
        dm.query(DownloadManager.Query().setFilterById(*recs.map { it.downloadId }.toLongArray()))?.use { c ->
            val iSt = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            while (c.moveToNext()) {
                val st = c.getInt(iSt)
                if (st != DownloadManager.STATUS_SUCCESSFUL && st != DownloadManager.STATUS_FAILED) return true
            }
        }
        return false
    }

    /** Lance le prochain épisode de la file si plus rien n'est en cours. */
    fun pump(): Unit = synchronized(LOCK) {
        while (true) {
            val q = queue()
            if (q.isEmpty() || anyActive()) return
            val next = q.first()
            saveQueue(q.drop(1))
            val started = runCatching { start(next.key, next.title, next.icon, next.url, next.ext) }.getOrDefault(false)
            if (started) return
        }
    }

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

    /** Vrai si le téléchargement de ce titre n'est pas encore terminé. */
    fun isRunning(key: String): Boolean {
        val r = records().firstOrNull { it.key == key } ?: return false
        dm.query(DownloadManager.Query().setFilterById(r.downloadId))?.use { c ->
            if (c.moveToFirst()) {
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                return st != DownloadManager.STATUS_SUCCESSFUL
            }
        }
        return false
    }

    fun list(): List<DownloadInfo> {
        pump()
        val queued = queue().mapIndexed { i, q ->
            DownloadInfo(DownloadRecord(-1L, q.key, q.title, q.icon, ""), DlState.QUEUED, 0f, File(dir(), ""), i + 1)
        }
        return queued + listStarted()
    }

    private fun listStarted(): List<DownloadInfo> {
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
        if (r.downloadId < 0) {
            // Pas encore lancé : on le retire simplement de la file.
            synchronized(LOCK) { saveQueue(queue().filter { it.key != r.key }) }
            return
        }
        runCatching { dm.remove(r.downloadId) }
        fileOf(r).delete()
        saveRecords(records().filter { it.downloadId != r.downloadId })
        pump()
    }

    /** Vide la file d'attente (les téléchargements déjà lancés continuent). */
    fun clearQueue() = synchronized(LOCK) { saveQueue(emptyList()) }

    companion object {
        /** Verrou commun à toutes les instances (écran + récepteur de fin de téléchargement). */
        private val LOCK = Any()
    }
}
