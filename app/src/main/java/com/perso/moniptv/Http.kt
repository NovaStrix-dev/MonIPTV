package com.perso.moniptv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Le serveur a répondu avec un code d'erreur HTTP (403, 404, 5xx…). */
class HttpStatusException(val code: Int) : IOException("Erreur du serveur (HTTP $code)")

object Http {
    /** Beaucoup de serveurs IPTV refusent les clients inconnus : on se présente comme VLC. */
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(SmartDns)
        // Secours par la pile réseau d'Android (celle des téléchargements), voir SystemNetwork.
        .addInterceptor(SystemNetwork)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .build()

    /**
     * Les connexions par VPN coupent plus souvent : une requête qui échoue sur une erreur
     * réseau passagère (délai dépassé, connexion coupée) est retentée une fois.
     * Les erreurs du serveur (HTTP 4xx/5xx) et les adresses introuvables ne sont pas retentées.
     */
    private fun <T> withRetry(block: () -> T): T {
        try {
            return block()
        } catch (e: IOException) {
            if (e is HttpStatusException || e is UnknownHostException) throw e
            if (e is InterruptedIOException && e.message == "Canceled") throw e
            Thread.sleep(1500)
            return block()
        }
    }

    fun get(url: String): String = withRetry {
        client.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code)
            r.body?.string() ?: ""
        }
    }

    /** Lit une réponse ligne par ligne sans tout charger en mémoire (grosses playlists M3U). */
    fun <T> getLines(url: String, block: (Sequence<String>) -> T): T = withRetry {
        client.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code)
            val body = r.body ?: throw IOException("Réponse vide")
            body.charStream().buffered().useLines(block)
        }
    }

    /** Un VPN est-il actif sur le téléphone ? (sert à donner un message d'erreur utile) */
    fun isVpnActive(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }.getOrDefault(false)
}

/**
 * Résolution d'adresses adaptée aux VPN.
 *
 * 1. On demande d'abord au DNS du téléphone (celui du VPN quand il est actif).
 * 2. Beaucoup de VPN filtrent les domaines « suspects » (protections anti-pub / anti-malware) et
 *    les serveurs IPTV en font souvent partie : le DNS répond « introuvable » ou une fausse adresse
 *    (0.0.0.0, 127.0.0.1…). Dans ce cas on interroge directement Cloudflare puis Google en DNS chiffré
 *    (HTTPS), que le filtre du VPN ne peut pas modifier.
 * 3. Les adresses IPv4 passent en premier : beaucoup de VPN bloquent l'IPv6, et une connexion IPv6
 *    qui ne répond pas faisait attendre l'appli jusqu'au délai maximal.
 */
object SmartDns : Dns {

    /** Client minimal pour le DNS chiffré : il ne doit pas lui-même passer par SmartDns. */
    private val bootstrap = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val resolvers: List<Dns> by lazy {
        listOf(
            doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
            doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
        )
    }

    private fun doh(url: String, vararg ips: String): Dns = DnsOverHttps.Builder()
        .client(bootstrap)
        .url(url.toHttpUrl())
        .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
        .includeIPv6(true)
        .build()

    /** Réponses du DNS chiffré gardées 10 minutes (évite une requête par affiche). */
    private class Entry(val addresses: List<InetAddress>, val until: Long)
    private val cache = ConcurrentHashMap<String, Entry>()

    override fun lookup(hostname: String): List<InetAddress> {
        // Adresse IP saisie directement : rien à résoudre.
        if (hostname.contains(':') || hostname.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
            return Dns.SYSTEM.lookup(hostname)
        }
        val system = try {
            Dns.SYSTEM.lookup(hostname).filterNot { isSinkhole(it) }
        } catch (e: UnknownHostException) {
            emptyList()
        }
        if (system.isNotEmpty()) return ipv4First(system)

        val now = System.currentTimeMillis()
        cache[hostname]?.takeIf { it.until > now }?.let { return it.addresses }

        for (r in resolvers) {
            val found = runCatching { r.lookup(hostname) }.getOrNull()
                ?.filterNot { isSinkhole(it) }
                .orEmpty()
            if (found.isNotEmpty()) {
                val sorted = ipv4First(found)
                cache[hostname] = Entry(sorted, now + 10 * 60_000)
                return sorted
            }
        }
        throw UnknownHostException("Adresse introuvable : $hostname")
    }

    private fun ipv4First(list: List<InetAddress>) = list.sortedBy { if (it is Inet4Address) 0 else 1 }

    /** Fausse adresse renvoyée par un filtre DNS pour bloquer un domaine. */
    private fun isSinkhole(a: InetAddress) = a.isAnyLocalAddress || a.isLoopbackAddress
}
