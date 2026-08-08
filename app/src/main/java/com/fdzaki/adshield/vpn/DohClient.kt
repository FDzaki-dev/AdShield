package com.fdzaki.adshield.vpn

import android.net.VpnService
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLContext

/**
 * DNS-over-HTTPS client (v3.11.0 — see PROJECT_STATE.md decision log).
 *
 * Added because plain-UDP port 53 forwarding (see
 * [com.fdzaki.adshield.vpn.dns.UpstreamForwarder.forwardToUpstream], extracted from
 * AdBlockVpnService in the v3.17.0 God-Class refactor — see PROJECT_STATE.md)
 * was confirmed by the user to fail totally on their network (matching
 * DNS_PROBE_FINISHED_BAD_SECURE_CONFIG-style total failure, ruled out as an
 * Android Private DNS / Chrome Secure DNS setting on their end — the network
 * itself blocks/breaks plain UDP:53). DoH runs over port 443 (indistinguishable
 * from ordinary HTTPS traffic to the network), so it survives networks that
 * block raw DNS.
 *
 * Every socket this opens MUST go through [VpnService.protect] (via the
 * custom [SSLSocketFactory] below) — otherwise its own HTTPS traffic would
 * get routed back into our own tun interface and deadlock/loop, exactly like
 * the plain-UDP upstream sockets in AdBlockVpnService already are protected.
 *
 * Explicitly NOT a replacement for the plain-UDP path: per user decision
 * (2026-08-05), DoH is tried FIRST, and only falls back to the existing
 * plain-UDP resolver chain ([Constants.UPSTREAM_DNS_SERVERS]) if every DoH
 * endpoint fails. This keeps the plain-UDP path as a safety net for networks
 * where DoH itself is blocked/broken instead, rather than a straight swap.
 */
object DohClient {

    /**
     * Attempts each endpoint in [Constants.DOH_ENDPOINTS] in order, returning
     * the first successful raw DNS wire-format response, or null if every
     * endpoint failed (caller is expected to fall back to plain UDP).
     *
     * @param dnsMessage raw DNS wire-format query bytes (same format already
     *   built by [com.fdzaki.adshield.vpn.dns.UpstreamForwarder] for the UDP path).
     */
    fun resolve(vpnService: VpnService, dnsMessage: ByteArray): ByteArray? {
        for (endpoint in com.fdzaki.adshield.util.Constants.DOH_ENDPOINTS) {
            try {
                val result = queryOne(vpnService, endpoint, dnsMessage)
                if (result != null) {
                    DohHealthMonitor.recordSuccess(endpoint)
                    return result
                }
                // Reached only on non-exceptional non-200 responses (see queryOne) —
                // still a failure, just one that didn't throw.
                DohHealthMonitor.recordEndpointFailure(endpoint, "HTTP non-200 response")
            } catch (e: Exception) {
                // v3.25.0 (see PROJECT_STATE.md "Krisis DNS/DoH"): this used to be a
                // bare swallow with zero diagnostic trail — the exact reason this
                // crisis was never root-caused. Now recorded with the real exception
                // type + message so DiagnosticsScreen can show it after the fact.
                DohHealthMonitor.recordEndpointFailure(endpoint, "${e.javaClass.simpleName}: ${e.message}")
                // try next endpoint
            }
        }
        DohHealthMonitor.recordFullFallback()
        return null
    }

    private fun queryOne(vpnService: VpnService, endpointUrl: String, dnsMessage: ByteArray): ByteArray? {
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpsURLConnection
        conn.sslSocketFactory = protectingSocketFactory(vpnService)
        conn.setRequestProperty("Connection", "keep-alive")
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = com.fdzaki.adshield.util.Constants.DOH_TIMEOUT_MS
        conn.readTimeout = com.fdzaki.adshield.util.Constants.DOH_TIMEOUT_MS
        conn.setRequestProperty("Content-Type", "application/dns-message")
        conn.setRequestProperty("Accept", "application/dns-message")
        // v3.25.0 — reliability/perf fix (see PROJECT_STATE.md "Krisis DNS/DoH"):
        // this used to call conn.disconnect() in a `finally` after EVERY single
        // query. Per java.net.HttpURLConnection docs, disconnect() signals the
        // underlying socket should NOT be kept alive for reuse — so on a DNS
        // Ad-Block session doing dozens/hundreds of lookups, every single one paid
        // a full fresh TLS handshake instead of reusing the JVM's built-in
        // keep-alive connection pool (the same pool that already makes ordinary
        // HTTPS browsing fast). Removing it is safe: both streams are still fully
        // read and closed via `.use {}` below (the actual requirement for a
        // connection to become eligible for reuse) — disconnect() was pure
        // overhead, not a correctness requirement. No behavior change to the
        // DoH-then-plain-UDP fallback logic in UpstreamForwarder, only latency
        // improves after the first successful query per endpoint.
        conn.outputStream.use { it.write(dnsMessage) }
        if (conn.responseCode != 200) return null
        return conn.inputStream.use { it.readBytes() }
    }

    // Perf (v4.1.0 — see PROJECT_STATE.md "Radikal Perf" batch): the v3.25.0
    // fix above removed the per-query disconnect() specifically so the JVM's
    // built-in HTTPS keep-alive/connection pool (and TLS session resumption)
    // could kick in across queries. It never actually did: protectingSocketFactory()
    // was still called fresh INSIDE queryOne() on every single call, and
    // Android's HttpsURLConnection connection-pool key includes the identity
    // of the SSLSocketFactory instance in use — a brand-new factory object
    // every query means every query looks like a different, unpoolable route,
    // so every DoH lookup (tried FIRST for every forwarded DNS query per
    // decision 2026-08-05) silently kept paying a full fresh TLS handshake
    // (~1-2 extra round-trips) no matter how many times the same endpoint was
    // queried. Caching ONE factory instance per live VpnService here — while
    // still calling protect() on every individual socket createSocket() makes,
    // so the security property is unchanged — is what actually turns on reuse.
    // A new VpnService instance (fresh VPN start) naturally invalidates the
    // cache via the `!==` identity check, so nothing can leak across restarts.
    @Volatile private var cachedFactory: SSLSocketFactory? = null
    @Volatile private var cachedForService: VpnService? = null
    private val factoryBuildLock = Any()

    private fun protectingSocketFactory(vpnService: VpnService): SSLSocketFactory {
        cachedFactory?.let { factory -> if (cachedForService === vpnService) return factory }
        synchronized(factoryBuildLock) {
            cachedFactory?.let { factory -> if (cachedForService === vpnService) return factory }
            val factory = buildProtectingSocketFactory(vpnService)
            cachedForService = vpnService
            cachedFactory = factory
            return factory
        }
    }

    /**
     * Wraps the platform's default [SSLSocketFactory] so every socket it
     * creates is immediately handed to [VpnService.protect] before use —
     * required so this DoH traffic bypasses our own tun interface instead of
     * looping back into it (same principle as `protect()` calls on the
     * plain-UDP upstream sockets elsewhere in this package).
     */
    private fun buildProtectingSocketFactory(vpnService: VpnService): SSLSocketFactory {
        val delegate = SSLContext.getInstance("TLS").apply { init(null, null, null) }.socketFactory
        return object : SSLSocketFactory() {
            override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
            override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

            override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
                vpnService.protect(s)
                return delegate.createSocket(s, host, port, autoClose)
            }

            override fun createSocket(host: String, port: Int): Socket {
                val s = Socket()
                vpnService.protect(s)
                s.connect(java.net.InetSocketAddress(host, port))
                return delegate.createSocket(s, host, port, true)
            }

            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): Socket {
                val s = Socket()
                vpnService.protect(s)
                s.bind(java.net.InetSocketAddress(localHost, localPort))
                s.connect(java.net.InetSocketAddress(host, port))
                return delegate.createSocket(s, host, port, true)
            }

            override fun createSocket(host: java.net.InetAddress, port: Int): Socket {
                val s = Socket()
                vpnService.protect(s)
                s.connect(java.net.InetSocketAddress(host, port))
                return delegate.createSocket(s, host.hostAddress, port, true)
            }

            override fun createSocket(
                address: java.net.InetAddress,
                port: Int,
                localAddress: java.net.InetAddress,
                localPort: Int
            ): Socket {
                val s = Socket()
                vpnService.protect(s)
                s.bind(java.net.InetSocketAddress(localAddress, localPort))
                s.connect(java.net.InetSocketAddress(address, port))
                return delegate.createSocket(s, address.hostAddress, port, true)
            }
        }
    }
}
