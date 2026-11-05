package com.perso.moniptv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Chemin réseau de secours, utilisé par toute l'appli (catalogue, affiches, lecteur) via [Http.client].
 *
 * Constat (issue #6) : derrière certains VPN, les téléchargements marchent (ils passent par le gestionnaire
 * de téléchargement d'Android, c.-à-d. la pile réseau standard du système) alors que l'actualisation et la
 * lecture échouent (elles passent par OkHttp et sa résolution d'adresses).
 *
 * Ce relais refait donc la requête avec la pile standard d'Android (`HttpURLConnection`, DNS du système),
 * exactement comme le gestionnaire de téléchargement :
 * - d'emblée quand un VPN est actif (ou après un premier succès du secours) ;
 * - sinon seulement si OkHttp échoue (erreur réseau, ou refus 403/451/5xx).
 *
 * Option « Contourner le VPN » (choisie par l'utilisateur) : les requêtes sont liées au réseau réel du
 * téléphone (Wi-Fi / mobile) au lieu du VPN. Possible seulement si l'appli VPN l'autorise.
 */
object SystemNetwork : Interceptor {

    private const val PREFS = "network"
    private const val KEY_BYPASS = "bypass_vpn"

    private lateinit var app: Context

    /** Le secours a déjà réussi pendant cette session : on l'utilise en premier. */
    @Volatile private var preferSystem = false

    /** Dernière erreur technique (pour les messages d'erreur). */
    @Volatile var lastDetail: String? = null
        private set

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    var bypassVpn: Boolean
        get() = ::app.isInitialized &&
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BYPASS, false)
        set(v) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BYPASS, v).apply()
        }

    private fun vpnActive(): Boolean = ::app.isInitialized && Http.isVpnActive(app)

    /** Réseau réel du téléphone (Wi-Fi ou données mobiles), hors VPN. */
    private fun underlyingNetwork(): Network? = runCatching {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        cm.allNetworks.firstOrNull { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }.getOrNull()

    private val retryCodes = setOf(403, 451, 500, 502, 503, 504)

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val bypass = bypassVpn && vpnActive()

        // VPN actif, contournement demandé, ou secours déjà validé : pile système d'abord.
        if (bypass || preferSystem || vpnActive()) {
            try {
                val r = system(chain, req, bypass)
                if (r.code !in retryCodes || bypass) {
                    preferSystem = true
                    return r
                }
                r.close()
            } catch (e: IOException) {
                lastDetail = describe(e)
                if (chain.call().isCanceled() || bypass) throw e
            }
            return chain.proceed(req)
        }

        // Pas de VPN : OkHttp d'abord, pile système en secours.
        val first = try {
            chain.proceed(req)
        } catch (e: IOException) {
            lastDetail = describe(e)
            if (chain.call().isCanceled()) throw e
            try {
                return system(chain, req, false).also { preferSystem = true }
            } catch (e2: IOException) {
                e.addSuppressed(e2)
                throw e
            }
        }
        if (first.code in retryCodes) {
            val alt = runCatching { system(chain, req, false) }.getOrNull()
            if (alt != null && alt.isSuccessful) {
                first.close()
                preferSystem = true
                return alt
            }
            alt?.close()
        }
        return first
    }

    fun describe(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        return "${root.javaClass.simpleName}: ${root.message ?: "-"}"
    }

    /** Requête par `HttpURLConnection` (pile standard d'Android, DNS du système). */
    private fun system(chain: Interceptor.Chain, req: Request, bypass: Boolean): Response {
        if (req.method != "GET" && req.method != "HEAD") throw IOException("Méthode non gérée : ${req.method}")
        val network = if (bypass) {
            underlyingNetwork() ?: throw IOException("Aucun réseau hors VPN disponible")
        } else null
        var url: URL = req.url.toUrl()
        var redirects = 0
        while (true) {
            val c = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = chain.connectTimeoutMillis().coerceAtLeast(15_000)
            c.readTimeout = chain.readTimeoutMillis()
            c.requestMethod = req.method
            for (i in 0 until req.headers.size) c.addRequestProperty(req.headers.name(i), req.headers.value(i))
            if (req.header("User-Agent") == null) c.setRequestProperty("User-Agent", Http.USER_AGENT)

            val code = try {
                c.responseCode
            } catch (e: IOException) {
                c.disconnect()
                throw e
            }
            if (code in 300..399 && redirects < 5) {
                val loc = c.getHeaderField("Location")
                if (loc != null) {
                    c.disconnect()
                    url = URL(url, loc)
                    redirects++
                    continue
                }
            }
            return toResponse(req, c, code)
        }
    }

    private fun toResponse(req: Request, c: HttpURLConnection, code: Int): Response {
        val headers = Headers.Builder()
        c.headerFields.forEach { (k, values) ->
            if (k != null) values.forEach { v -> headers.addUnsafeNonAscii(k, v) }
        }
        val raw: InputStream = (if (code >= 400) c.errorStream else runCatching { c.inputStream }.getOrNull())
            ?: ByteArrayInputStream(ByteArray(0))
        val stream = object : FilterInputStream(raw) {
            override fun close() {
                try { super.close() } finally { c.disconnect() }
            }
        }
        val type = c.contentType?.toMediaTypeOrNull()
        val length = c.contentLengthLong
        val body = object : ResponseBody() {
            private val src by lazy { stream.source().buffer() }
            override fun contentType(): MediaType? = type
            override fun contentLength(): Long = length
            override fun source(): BufferedSource = src
        }
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(c.responseMessage ?: "")
            .headers(headers.build())
            .body(body)
            .build()
    }
}
