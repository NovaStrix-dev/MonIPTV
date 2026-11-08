package com.perso.moniptv

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import android.view.View
import android.view.WindowManager
import android.os.SystemClock
import androidx.media3.session.MediaSession
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import android.view.KeyEvent
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import org.json.JSONObject

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    /** Session média (#25) : indique à Android TV qu'une vidéo est en cours (pas d'économiseur d'écran ni de veille). */
    private var mediaSession: MediaSession? = null
    /** Début de la lecture en cours (pour le journal de diagnostic). */
    private var playStartedAt = 0L
    private lateinit var playerView: PlayerView
    private var url: String = ""
    private var live = false
    private var meta: WatchMeta? = null
    /** Titre affiché à côté du bouton Retour (#20). */
    private var playTitle: String = ""

    /** Zapping (#17) : chaînes de la catégorie choisie et position de la chaîne en cours. */
    private var channels: List<LiveChannel> = emptyList()
    private var groupName: String = ""
    private var channelIndex by mutableStateOf(-1)
    private var channelName by mutableStateOf("")

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
        playTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        if (live) {
            channels = LivePlaylist.channels
            groupName = LivePlaylist.groupName
            channelName = intent.getStringExtra(EXTRA_TITLE) ?: ""
            // Après une rotation ou un retour dans l'appli, on reprend la dernière chaîne regardée.
            savedInstanceState?.getString(STATE_URL)?.let { url = it }
            savedInstanceState?.getString(STATE_NAME)?.let { channelName = it }
            channelIndex = channels.indexOfFirst { it.url == url }
            if (channelIndex < 0) channels = emptyList()
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Écran maintenu allumé sur toute la fenêtre du lecteur, pas seulement la vue vidéo (#25).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            controllerShowTimeoutMs = 3500
            // Bouton « Sous-titres » dans la barre du lecteur (masqué par défaut dans Media3).
            // Il liste les sous-titres du fichier ou du flux et permet de les désactiver.
            setShowSubtitleButton(true)
            // #27 : la barre de progression avançait d'1/20 de la durée par flèche (≈ 4 min) : 10 s à la place.
            findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.setKeyTimeIncrement(SEEK_STEP_MS)
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
                    // Zapping (#17) : chaîne précédente / suivante dans la même catégorie.
                    if (live && channels.size > 1 && controlsVisible) {
                        Column(
                            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ZapButton(Icons.Filled.KeyboardArrowUp, "Chaîne précédente") { zap(-1) }
                            Spacer(Modifier.height(12.dp))
                            ZapButton(Icons.Filled.KeyboardArrowDown, "Chaîne suivante") { zap(+1) }
                        }
                    }
                    // Retour à la liste (#20) + titre : nom de la chaîne et position dans la catégorie,
                    // ou titre du film / de la série et de l'épisode.
                    if (controlsVisible) {
                        val heading = if (live) channelName else (meta?.title ?: playTitle)
                        val sub = when {
                            live && channels.size > 1 && channelIndex >= 0 -> "$groupName · ${channelIndex + 1}/${channels.size}"
                            live -> null
                            else -> meta?.subtitle
                        }
                        Row(
                            Modifier.align(Alignment.TopStart).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BackButton { finish() }
                            if (heading.isNotEmpty()) {
                                Spacer(Modifier.width(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = androidx.compose.ui.graphics.Color(0x99000000),
                                    contentColor = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.widthIn(max = 420.dp),
                                ) {
                                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                        Text(heading, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                        if (!sub.isNullOrEmpty()) {
                                            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (live) {
            outState.putString(STATE_URL, url)
            outState.putString(STATE_NAME, channelName)
        }
    }

    /**
     * #27 : flèches gauche / droite de la télécommande, commandes masquées (film, épisode) : recule / avance de 10 s
     * et affiche les commandes. Commandes visibles, les flèches servent à se déplacer (sur la barre : pas de 10 s).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player
        if (!live && p != null && !controlsVisible &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) p.seekBack() else p.seekForward()
                playerView.showController()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Touches télécommande / clavier : chaîne + / chaîne - (et haut / bas quand les commandes sont masquées). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (live && channels.size > 1) {
            when (keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { zap(-1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> { zap(+1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Passe à la chaîne voisine de la même catégorie (on boucle au début / à la fin de la liste). */
    private fun zap(step: Int) {
        if (channels.size < 2) return
        val start = if (channelIndex < 0) 0 else channelIndex
        val i = Math.floorMod(start + step, channels.size)
        val c = channels[i]
        channelIndex = i
        channelName = c.name
        url = c.url
        Diagnostics.log(this, "zapping vers « ${c.name} »")
        player?.let { p ->
            p.setMediaItem(mediaItemFor(url))
            p.playWhenReady = true
            p.prepare()
        }
        playerView.showController()
    }

    override fun onStart() {
        super.onStart()
        if (url.isNotEmpty()) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        val why = if (isFinishing) "lecteur fermé (Retour)" else "lecteur passé en arrière-plan (veille, économiseur d'écran ou accueil du boîtier)"
        Diagnostics.log(this, "$why après ${minutesPlayed()} min")
        releasePlayer()
    }

    private fun minutesPlayed(): Long =
        if (playStartedAt == 0L) 0 else (SystemClock.elapsedRealtime() - playStartedAt) / 60_000

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
            // #27 : boutons avance / retour, touches avance rapide / retour rapide : 10 s.
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
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
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) Diagnostics.log(this@PlayerActivity, "fin de la vidéo après ${minutesPlayed()} min")
            }

            override fun onPlayerError(error: PlaybackException) {
                Diagnostics.log(this@PlayerActivity, "erreur lecteur après ${minutesPlayed()} min : ${error.errorCodeName} · ${SystemNetwork.describe(error)}")
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

        p.setMediaItem(mediaItemFor(url))
        if (!live) {
            val pos = meta?.let { WatchProgress.position(this, it.key) }?.takeIf { it > 0 }
                ?: legacyPositions.getLong(url, 0L)
            if (pos > 0) p.seekTo(pos)
        }
        p.playWhenReady = true
        p.prepare()
        playerView.player = p
        player = p
        mediaSession = runCatching { MediaSession.Builder(this, p).build() }
            .onFailure { Diagnostics.log(this, "session média impossible : ${it.javaClass.simpleName}") }
            .getOrNull()
        playStartedAt = SystemClock.elapsedRealtime()
        val what = if (live) "chaîne « $channelName »" else "« ${meta?.title ?: playTitle} »${meta?.subtitle?.let { " $it" } ?: ""}"
        Diagnostics.log(this, "lecture de $what${if (url.startsWith("file:")) " (fichier téléchargé)" else ""}")
    }

    private fun mediaItemFor(u: String): MediaItem {
        val item = MediaItem.Builder().setUri(u)
        if (u.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return item.build()
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
        mediaSession?.release()
        mediaSession = null
        p.release()
        player = null
        playerView.player = null
    }

    companion object {
        private const val SEEK_STEP_MS = 10_000L
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LIVE = "live"
        private const val EXTRA_META = "meta"

        private const val STATE_URL = "zap_url"
        private const val STATE_NAME = "zap_name"

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

        /**
         * Lance une chaîne TV en donnant au lecteur les chaînes de la catégorie affichée (#17).
         * La liste reste en mémoire (elle peut contenir des milliers de chaînes, trop pour un Intent).
         */
        fun startLive(ctx: Context, list: List<Media>, current: Media, group: String) {
            val url = current.url ?: return
            LivePlaylist.channels = list.mapNotNull { m -> m.url?.let { LiveChannel(m.name, it) } }
            LivePlaylist.groupName = group
            start(ctx, url, current.name, live = true)
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

/** Une chaîne de la liste de zapping. */
data class LiveChannel(val name: String, val url: String)

/** Chaînes de la catégorie ouverte au moment de lancer le direct (#17). */
object LivePlaylist {
    @Volatile var channels: List<LiveChannel> = emptyList()
    @Volatile var groupName: String = ""
}

@androidx.compose.runtime.Composable
private fun ZapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = androidx.compose.ui.graphics.Color(0x99000000),
        contentColor = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.padding(12.dp).size(32.dp))
    }
}

/** Bouton « Retour » du lecteur (#20) : revient à la liste des films, séries ou chaînes. */
@androidx.compose.runtime.Composable
private fun BackButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = androidx.compose.ui.graphics.Color(0x99000000),
        contentColor = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Retour")
        }
    }
}
