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
 * Added because plain-UDP port 53 forwarding (see [AdBlockVpnService.forwardToUpstream])
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
     *   built by [AdBlockVpnService.buildForwardedRequest] for the UDP path).
     */
    fun resolve(vpnService: VpnService, dnsMessage: ByteArray): ByteArray? {
        for (endpoint in com.fdzaki.adshield.util.Constants.DOH_ENDPOINTS) {
            try {
                val result = queryOne(vpnService, endpoint, dnsMessage)
                if (result != null) return result
            } catch (_: Exception) {
                // try next endpoint
            }
        }
        return null
    }

    private fun queryOne(vpnService: VpnService, endpointUrl: String, dnsMessage: ByteArray): ByteArray? {
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpsURLConnection
        conn.sslSocketFactory = protectingSocketFactory(vpnService)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = com.fdzaki.adshield.util.Constants.DOH_TIMEOUT_MS
        conn.readTimeout = com.fdzaki.adshield.util.Constants.DOH_TIMEOUT_MS
        conn.setRequestProperty("Content-Type", "application/dns-message")
        conn.setRequestProperty("Accept", "application/dns-message")
        try {
            conn.outputStream.use { it.write(dnsMessage) }
            if (conn.responseCode != 200) return null
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Wraps the platform's default [SSLSocketFactory] so every socket it
     * creates is immediately handed to [VpnService.protect] before use —
     * required so this DoH traffic bypasses our own tun interface instead of
     * looping back into it (same principle as `protect()` calls on the
     * plain-UDP upstream sockets elsewhere in this package).
     */
    private fun protectingSocketFactory(vpnService: VpnService): SSLSocketFactory {
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
