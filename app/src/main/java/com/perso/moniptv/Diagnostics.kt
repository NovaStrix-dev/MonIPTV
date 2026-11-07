package com.perso.moniptv

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic (#25) : garde une trace de ce qui arrête l'appli, pour pouvoir l'envoyer à Claude.
 * - Plantage Kotlin/Java : écrit dans files/crash.txt, affiché au démarrage suivant.
 * - Arrêt anormal vu par Android (Android 11+) : plantage natif, blocage (ANR), manque de mémoire…
 *   pendant que l'appli était à l'écran.
 * - Journal du lecteur : début de lecture, erreurs, arrêt (appli quittée ou mise en veille), avec l'heure.
 */
object Diagnostics {
    private const val PREFS = "diag"
    private const val KEY_LOG = "player_log"
    private const val KEY_LAST_EXIT = "last_exit_ts"
    private const val KEY_PENDING = "pending_report"
    private const val MAX_LINES = 40

    private val fmt get() = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun crashFile(ctx: Context) = File(ctx.filesDir, "crash.txt")

    /** À appeler dans Application.onCreate. */
    fun init(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                crashFile(app).writeText(
                    "Plantage le ${fmt.format(Date())} (fil « ${thread.name} »)\n$sw"
                )
                log(app, "PLANTAGE : ${error.javaClass.simpleName}: ${error.message}", sync = true)
            }
            previous?.uncaughtException(thread, error)
        }
        runCatching { checkExitReasons(app) }
    }

    /** Ajoute une ligne horodatée au journal du lecteur (40 dernières lignes gardées). */
    fun log(ctx: Context, line: String, sync: Boolean = false) {
        val p = prefs(ctx)
        val lines = (p.getString(KEY_LOG, "") ?: "").lines().filter { it.isNotBlank() }
        val updated = (lines + "${fmt.format(Date())}  $line").takeLast(MAX_LINES).joinToString("\n")
        val e = p.edit().putString(KEY_LOG, updated)
        if (sync) e.commit() else e.apply()
    }

    /** Android 11+ : cherche un arrêt anormal survenu alors que l'appli était à l'écran. */
    private fun checkExitReasons(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return
        val exits = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 5)
        val p = prefs(ctx)
        val lastSeen = p.getLong(KEY_LAST_EXIT, 0L)
        val newest = exits.maxOfOrNull { it.timestamp } ?: return
        if (lastSeen == 0L) { // première fois : on ne remonte pas l'historique ancien
            p.edit().putLong(KEY_LAST_EXIT, newest).apply()
            return
        }
        val abnormal = exits.filter { e ->
            e.timestamp > lastSeen &&
                e.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE &&
                e.reason in setOf(
                    ApplicationExitInfo.REASON_CRASH,
                    ApplicationExitInfo.REASON_CRASH_NATIVE,
                    ApplicationExitInfo.REASON_ANR,
                    ApplicationExitInfo.REASON_LOW_MEMORY,
                    ApplicationExitInfo.REASON_SIGNALED,
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                    ApplicationExitInfo.REASON_OTHER,
                )
        }
        val e = p.edit().putLong(KEY_LAST_EXIT, maxOf(newest, lastSeen))
        if (abnormal.isNotEmpty()) {
            val text = abnormal.joinToString("\n") { x ->
                "Arrêt par Android le ${fmt.format(Date(x.timestamp))} : ${reasonName(x.reason)}" +
                    (x.description?.let { " ($it)" } ?: "") +
                    " · mémoire utilisée ${x.pss / 1024} Mo"
            }
            e.putString(KEY_PENDING, text)
        }
        e.apply()
    }

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "plantage"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "plantage natif (lecteur / décodeur)"
        ApplicationExitInfo.REASON_ANR -> "appli bloquée (ANR)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "manque de mémoire"
        ApplicationExitInfo.REASON_SIGNALED -> "arrêtée par le système"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "trop de ressources utilisées"
        else -> "autre ($r)"
    }

    /** Rapport à montrer au démarrage, s'il y a eu un plantage ou un arrêt anormal depuis la dernière fois. */
    fun pendingReport(ctx: Context): String? {
        val crash = crashFile(ctx).takeIf { it.exists() }?.readText()
        val exit = prefs(ctx).getString(KEY_PENDING, null)
        if (crash == null && exit == null) return null
        return fullReport(ctx, listOfNotNull(exit, crash).joinToString("\n\n"))
    }

    /** Une fois le rapport vu : on ne le remontre plus au démarrage (le journal du lecteur reste). */
    fun clearPending(ctx: Context) {
        crashFile(ctx).delete()
        prefs(ctx).edit().remove(KEY_PENDING).apply()
    }

    /** Rapport complet : appareil, version, problème éventuel et journal du lecteur. */
    fun fullReport(ctx: Context, problem: String? = null): String {
        val version = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val log = prefs(ctx).getString(KEY_LOG, "").orEmpty().ifBlank { "(vide)" }
        return buildString {
            appendLine("MonIPTV $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            if (!problem.isNullOrBlank()) {
                appendLine()
                appendLine(problem.trim())
            }
            appendLine()
            appendLine("Journal du lecteur :")
            append(log)
        }
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Diagnostic MonIPTV", text))
        Toast.makeText(ctx, "Rapport copié", Toast.LENGTH_SHORT).show()
    }
}

/** Fenêtre de diagnostic : texte défilant + bouton Copier. */
@Composable
fun DiagnosticDialog(title: String, intro: String, report: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(intro, style = MaterialTheme.typography.bodyMedium)
                Text(
                    report,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { Diagnostics.copy(ctx, report) }) { Text("Copier") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Au démarrage : montre le rapport s'il y a eu un plantage ou un arrêt anormal. */
@Composable
fun CrashReportHost() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(runCatching { Diagnostics.pendingReport(ctx) }.getOrNull()) }
    val r = report ?: return
    DiagnosticDialog(
        title = "MonIPTV s'est arrêtée",
        intro = "L'appli s'est arrêtée de façon anormale la dernière fois. Copiez ce rapport et envoyez-le à Claude.",
        report = r,
        onDismiss = {
            Diagnostics.clearPending(ctx)
            report = null
        },
    )
}
