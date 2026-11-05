package com.perso.moniptv

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONObject

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var url: String = ""
    private var live = false
    private var meta: WatchMeta? = null

    /** Anciennes positions (versions précédentes de l'appli), clé = adresse de lecture. */
    private val legacyPositions by lazy { getSharedPreferences("positions", MODE_PRIVATE) }
    private val settings by lazy { getSharedPreferences("player", MODE_PRIVATE) }

    /** Apparence des sous-titres (issue #5) et état de l'interface au-dessus du lecteur. */
    private var subStyle by mutableStateOf(SubtitleStyle())
    private var controlsVisible by mutableStateOf(false)
    private var styleDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra(EXTRA_URL) ?: ""
        live = intent.getBooleanExtra(EXTRA_LIVE, false)
        meta = intent.getStringExtra(EXTRA_META)?.let { decodeMeta(it) }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            controllerShowTimeoutMs = 3500
            // Bouton « Sous-titres » dans la barre du lecteur (masqué par défaut dans Media3).
            // Il liste les sous-titres du fichier ou du flux et permet de les désactiver.
            setShowSubtitleButton(true)
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                controlsVisible = v == View.VISIBLE
            })
        }
        subStyle = SubtitleStyle.load(this)
        playerView.subtitleView?.let { subStyle.applyTo(it) }

        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())
                    // Bouton « Apparence des sous-titres », visible avec les commandes du lecteur.
                    if (controlsVisible) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = androidx.compose.ui.graphics.Color(0x99000000),
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clickable { styleDialog = true },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.ClosedCaption, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Style des sous-titres")
                            }
                        }
                    }
                    if (styleDialog) {
                        SubtitleStyleDialog(
                            style = subStyle,
                            onChange = { s ->
                                subStyle = s
                                SubtitleStyle.save(this@PlayerActivity, s)
                                playerView.subtitleView?.let { s.applyTo(it) }
                            },
                            onDismiss = { styleDialog = false },
                        )
                    }
                }
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (url.isEmpty()) {
            finish()
            return
        }
        intent.getStringExtra(EXTRA_TITLE)?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
    }

    override fun onStart() {
        super.onStart()
        if (url.isNotEmpty()) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initPlayer() {
        if (player != null) return
        // Même client réseau que le reste de l'appli (résolution d'adresses adaptée aux VPN).
        val okhttp = Http.client.newBuilder()
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val http = OkHttpDataSource.Factory(okhttp).setUserAgent(Http.USER_AGENT)
        val dataSource = DefaultDataSource.Factory(this, http) // gère aussi les fichiers locaux
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val p = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()

        // Sous-titres : on réactive automatiquement la dernière langue choisie.
        val subLang = settings.getString(PREF_SUB_LANG, null)
        if (subLang != null && subLang != SUB_OFF) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage(if (subLang == SUB_UND) null else subLang)
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    p.seekToDefaultPosition()
                    p.prepare()
                } else {
                    val network = error.errorCode in 2000..2999 // erreurs de connexion / réseau
                    val hint = if (network && !url.startsWith("file:") && Http.isVpnActive(this@PlayerActivity)) {
                        "\nVPN actif : le fournisseur bloque peut-être la lecture via ce VPN."
                    } else ""
                    Toast.makeText(
                        this@PlayerActivity,
                        "Lecture impossible (${error.errorCodeName})$hint\nDétail : ${SystemNetwork.describe(error)}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            override fun onTracksChanged(tracks: Tracks) = rememberSubtitleChoice(tracks)
        })

        val item = MediaItem.Builder().setUri(url)
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        p.setMediaItem(item.build())
        if (!live) {
            val pos = meta?.let { WatchProgress.position(this, it.key) }?.takeIf { it > 0 }
                ?: legacyPositions.getLong(url, 0L)
            if (pos > 0) p.seekTo(pos)
        }
        p.playWhenReady = true
        p.prepare()
        playerView.player = p
        player = p
    }

    /** Mémorise la langue des sous-titres choisie (ou « aucun ») pour les prochaines lectures. */
    private fun rememberSubtitleChoice(tracks: Tracks) {
        val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (text.isEmpty()) return
        val selected = text.firstNotNullOfOrNull { g ->
            (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it).language ?: SUB_UND }
        }
        val saved = settings.getString(PREF_SUB_LANG, null)
        val value = when {
            selected != null -> selected
            // Rien de sélectionné alors que la langue habituelle existe ici : l'utilisateur a choisi « Aucun ».
            saved != null && saved != SUB_OFF && text.any { g ->
                (0 until g.length).any { (g.getTrackFormat(it).language ?: SUB_UND) == saved }
            } -> SUB_OFF
            else -> return
        }
        if (value != saved) settings.edit().putString(PREF_SUB_LANG, value).apply()
    }

    private fun releasePlayer() {
        val p = player ?: return
        if (!live) {
            val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            val pos = p.currentPosition
            val m = meta
            if (m != null) {
                WatchProgress.record(this, m, pos, duration)
                legacyPositions.edit().remove(url).apply()
            } else {
                val e = legacyPositions.edit()
                // Reprise automatique là où on s'était arrêté (sauf si fini).
                if (duration > 0 && pos > duration - 60_000) e.remove(url) else e.putLong(url, pos)
                e.apply()
            }
        }
        p.release()
        player = null
        playerView.player = null
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LIVE = "live"
        private const val EXTRA_META = "meta"

        private const val PREF_SUB_LANG = "subtitle_lang"
        private const val SUB_OFF = "off"
        private const val SUB_UND = "und"

        fun start(ctx: Context, url: String, title: String, live: Boolean, meta: WatchMeta? = null) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_LIVE, live)
                    .putExtra(EXTRA_META, meta?.let { encodeMeta(it) })
            )
        }

        private fun encodeMeta(m: WatchMeta): String = JSONObject()
            .put("key", m.key).put("type", m.type.name).put("title", m.title)
            .put("subtitle", m.subtitle ?: "").put("icon", m.icon ?: "").put("url", m.url)
            .put("seriesId", m.seriesId ?: "")
            .toString()

        private fun decodeMeta(s: String): WatchMeta? = runCatching {
            val o = JSONObject(s)
            WatchMeta(
                key = o.getString("key"),
                type = ContentType.valueOf(o.getString("type")),
                title = o.getString("title"),
                subtitle = o.optString("subtitle").ifEmpty { null },
                icon = o.optString("icon").ifEmpty { null },
                url = o.getString("url"),
                seriesId = o.optString("seriesId").ifEmpty { null },
            )
        }.getOrNull()
    }
}
