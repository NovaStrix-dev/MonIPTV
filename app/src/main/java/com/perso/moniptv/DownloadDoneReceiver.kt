package com.perso.moniptv

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fin d'un téléchargement : lance l'épisode suivant de la file d'attente (#16), même appli fermée. */
class DownloadDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val pending = goAsync()
        Thread {
            try {
                runCatching { Downloads(context.applicationContext).pump() }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
