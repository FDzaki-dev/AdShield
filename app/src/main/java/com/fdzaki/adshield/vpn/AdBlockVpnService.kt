package com.fdzaki.adshield.vpn

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.fdzaki.adshield.MainActivity
import com.fdzaki.adshield.R
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.data.DnsCache
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.data.db.AppDatabase
import com.fdzaki.adshield.data.db.DomainLogEntity
import com.fdzaki.adshield.receiver.RestartReceiver
import com.fdzaki.adshield.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)

    // IMPORTANT (fixed 2026-08-03, see PROJECT_STATE.md incident log):
    // the packet-read loop below runs forever on its own dedicated thread
    // and must NEVER share a thread pool with forwardToUpstream(). A single
    // shared single-thread executor previously caused every forwarded query
    // to queue behind the infinite loop and never actually run — meaning
    // no non-blocked domain could ever resolve while DNS mode was on.
    private val loopExecutor = Executors.newSingleThreadExecutor()
    private val forwardExecutor = Executors.newFixedThreadPool(4)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var settingsRepository: SettingsRepository
    private val blocklist = BlocklistManager.getInstance()

    // Small cache so repeated queries from the same app don't re-hit
    // PackageManager every time; cleared each time the VPN restarts.
    private val uidToPackageCache = java.util.concurrent.ConcurrentHashMap<Int, String?>()

    // Perf (v3.6.0, see PROJECT_STATE.md decision #11b): one persistent UDP
    // socket per forwardExecutor worker thread instead of creating/protect()ing/
    // destroying a new DatagramSocket for every single forwarded DNS query.
    // Safe without any demux logic because each of forwardExecutor's 4 worker
    // threads only ever touches its OWN thread-local socket, synchronously,
    // one query at a time — no cross-thread sharing, so no risk of mismatched
    // replies. openUpstreamSockets tracks every live socket so stopVpn() can
    // close them all deterministically instead of leaking them for the
    // lifetime of the (never-shutdown) forwardExecutor threads.
    private val upstreamSocket = ThreadLocal<DatagramSocket>()
    private val openUpstreamSockets = java.util.concurrent.ConcurrentHashMap.newKeySet<DatagramSocket>()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val isModeSwitch = intent.getBooleanExtra(EXTRA_MODE_SWITCH, false)
                stopVpn(isModeSwitch)
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        // START_STICKY: if the system kills this service under memory pressure,
        // it restarts automatically with a null intent, so protection resumes
        // even without the user reopening the app.
        return START_STICKY
    }

    private fun startVpn() {
        if (running.get()) return

        uidToPackageCache.clear()
        // v3.7.0: never let cached answers survive across a VPN restart — the
        // network (WiFi<->data, different resolver, etc.) may well have
        // changed underneath, and a stale cached A record served on the new
        // network is a correctness bug, not just a perf one.
        DnsCache.clear()

        // Feedback audit finding (v3.8.1): setWasRunning(true)/setActiveMode(DNS_ADBLOCK)
        // used to fire unconditionally right here, BEFORE builder.establish() below even
        // ran — and were never reverted if establish() failed. SettingsRepository.activeMode
        // is the single source of truth read by both QS tiles (DnsTileService/WarpTileService)
        // and MainViewModel.vpnActive (see MainViewModel/HomeScreen), so a failed VPN
        // interface used to leave the tile AND the Home ring both stuck showing "ACTIVE"
        // forever — a silent false positive with no correction until the user happened to
        // open Diagnostics. WARP's path (WarpForegroundService) already gated this
        // correctly on `if (connected)`; DNS mode did not. Now symmetric: the write only
        // happens after establish() is confirmed to have actually produced an interface —
        // see the success tail below and the explicit setActiveMode(NONE) in the failure
        // branch a few lines down.
        serviceScope.launch {
            blocklist.loadDefaultList(applicationContext)
            blocklist.loadCachedRemoteListIfPresent(applicationContext)
            blocklist.setCustomBlocked(settingsRepository.customBlockedDomains.firstValue())
            blocklist.setCustomAllowed(settingsRepository.customAllowedDomains.firstValue())
            blocklist.setWhitelistedApps(settingsRepository.whitelistedApps.firstValue())
        }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(Constants.VPN_ADDRESS, Constants.VPN_ADDRESS_PREFIX)
            .addDnsServer(Constants.VPN_ADDRESS)
            .addRoute(Constants.VPN_ROUTE, 32) // ONLY DNS traffic is routed into the tun
            .setMtu(Constants.VPN_MTU)
            .setBlocking(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            _lastError.value = "Gagal membuat antarmuka VPN: ${e.message}"
            null
        }

        val iface = vpnInterface
        if (iface == null) {
            // establish() can also return null without throwing (e.g. another
            // VPN app grabbed the interface first) — surface a message either
            // way instead of silently doing nothing, so this is diagnosable
            // from the Diagnostics screen instead of just "toggle did nothing".
            if (_lastError.value == null) {
                _lastError.value = "Antarmuka VPN gagal dibuat (establish() null). " +
                    "Kemungkinan ada VPN/app lain yang sedang memegang koneksi VPN."
            }
            // Explicitly force NONE rather than leaving activeMode untouched: this stop
            // path also runs after a mode-switch (WARP->DNS), where the old WARP entry has
            // already been told to stop but activeMode may still read WARP_TUNNEL — leaving
            // it as-is would make the tile/ring show the OLD mode as active even though
            // nothing is actually running.
            serviceScope.launch {
                settingsRepository.setWasRunning(false)
                settingsRepository.setActiveMode(com.fdzaki.adshield.util.AppMode.NONE)
            }
            return
        }
        _lastError.value = null
        running.set(true)
        serviceScope.launch {
            settingsRepository.setWasRunning(true)
            settingsRepository.setActiveMode(com.fdzaki.adshield.util.AppMode.DNS_ADBLOCK)
        }
        startForeground(Constants.NOTIF_ID, buildNotification())
        loopExecutor.execute { runPacketLoop(iface) }
        prefetchPopularDomains()
    }

    /**
     * DNS prefetch / cache warm-up (v3.9.0 — Internet Surfing Optimization,
     * batch 2). Resolves [Constants.POPULAR_PREFETCH_DOMAINS] in the
     * background shortly after startup and seeds [DnsCache] with the
     * answers, so the first REAL query for any of them from an app is
     * already a cache hit instead of a cold upstream round-trip.
     *
     * Deliberately does NOT check the blocklist first: skipping that check
     * removes a startup-ordering dependency on the blocklist-load coroutine
     * above, and is harmless either way — a cached answer for a domain that
     * turns out to be blocked is simply never read, because the packet loop
     * always checks `blocklist.isBlocked()` BEFORE it ever looks at
     * [DnsCache] (see `runPacketLoop`). Uses its own dedicated protect()'d
     * socket, entirely separate from the pooled per-worker-thread sockets in
     * `forwardToUpstream()`, so a slow/failed prefetch run can never affect
     * a real in-flight query.
     */
    private fun prefetchPopularDomains() {
        serviceScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(Constants.PREFETCH_START_DELAY_MS)
            if (!running.get()) return@launch
            val socket = try {
                DatagramSocket().also { protect(it); it.soTimeout = 1500 }
            } catch (_: Exception) {
                return@launch
            }
            try {
                for (domain in Constants.POPULAR_PREFETCH_DOMAINS) {
                    if (!running.get()) break
                    if (DnsCache.get(domain, DNS_QTYPE_A) == null) {
                        runCatching { prefetchOne(socket, domain) }
                    }
                    kotlinx.coroutines.delay(Constants.PREFETCH_QUERY_GAP_MS)
                }
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    /** One prefetch lookup for a single domain, reusing the same resolver-fallback
     *  order as real queries. Silent on any failure — a missed prefetch just means
     *  that domain's first real query pays the normal upstream cost, same as today. */
    private fun prefetchOne(socket: DatagramSocket, domain: String) {
        // Fixed marker transaction ID: this reply is only ever consumed by
        // extractCacheableTtlSeconds()/DnsCache.put() below, never written back
        // to any app on the tun interface, so it doesn't need to match anything.
        val txId = byteArrayOf(0x50, 0x50)
        val request = DnsPacket.buildQueryMessage(domain, DNS_QTYPE_A, txId)
        val replyBuf = ByteArray(1500)
        for (server in Constants.UPSTREAM_DNS_SERVERS) {
            try {
                socket.send(DatagramPacket(request, request.size, InetSocketAddress(server, Constants.DNS_PORT)))
                val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                socket.receive(replyPacket)
                val message = replyBuf.copyOf(replyPacket.length)
                DnsPacket.extractCacheableTtlSeconds(message)?.let { ttl ->
                    DnsCache.put(domain, DNS_QTYPE_A, message, ttl)
                }
                return
            } catch (_: java.net.SocketTimeoutException) {
                // try next resolver
            } catch (_: java.io.IOException) {
                // try next resolver
            }
        }
    }

    private fun stopVpn(isModeSwitch: Boolean = false) {
        running.set(false)
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        closeUpstreamSockets()
        serviceScope.launch {
            settingsRepository.setWasRunning(false)
            // When this stop is just the "turn off DNS mode" half of a
            // DNS->WARP switch, DON'T write NONE here: WarpForegroundService
            // is about to write WARP_TUNNEL from its own coroutine, and the
            // two writes race on Dispatchers.IO with no ordering guarantee.
            // Only a genuine standalone stop (user pressed Stop, no other
            // mode starting) should reset activeMode to NONE.
            if (!isModeSwitch) {
                settingsRepository.setActiveMode(com.fdzaki.adshield.util.AppMode.NONE)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runPacketLoop(iface: ParcelFileDescriptor) {
        val input = FileInputStream(iface.fileDescriptor)
        val output = FileOutputStream(iface.fileDescriptor)
        val buffer = ByteArray(Constants.VPN_MTU)

        while (running.get()) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running.get()) continue else break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            val query = DnsPacket.parse(packetCopy, length) ?: continue

            // Whitelisted apps bypass blocking entirely. Only pay the UID
            // lookup cost when at least one app is actually whitelisted —
            // this keeps the hot path cheap for the common case (no whitelist).
            val bypassForWhitelistedApp = blocklist.hasWhitelistedApps() && isFromWhitelistedApp(query)

            val blocked = !bypassForWhitelistedApp && blocklist.isBlocked(query.queryDomain)

            if (blocked) {
                writeBlockedResponse(output, query)
                logAndCount(query.queryDomain, true)
            } else {
                // v3.7.0 DNS cache: serve straight from the packet-loop thread
                // on a hit — no executor hop, no socket round-trip. Falls
                // through to the normal async-forward path on a miss.
                val cached = DnsCache.get(query.queryDomain, DnsPacket.qtypeOf(query))
                if (cached != null) {
                    writeCachedResponse(output, query, cached)
                    logAndCount(query.queryDomain, false)
                } else {
                    // Fire-and-forget async forward so a slow upstream lookup never
                    // stalls the packet loop for other concurrent queries. Must use
                    // forwardExecutor (NOT loopExecutor) — see field comment above.
                    forwardExecutor.execute { forwardToUpstream(query, output) }
                    logAndCount(query.queryDomain, false)
                }
            }
        }

        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    /**
     * Resolves which installed app actually sent this DNS query (via
     * ConnectivityManager.getConnectionOwnerUid, API 29+) and checks it
     * against the user's per-app whitelist. Below API 29 this silently
     * returns false — per-app whitelist just has no effect on older
     * Android, since the OS doesn't expose this attribution API there.
     */
    private fun isFromWhitelistedApp(query: DnsPacket.ParsedQuery): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return false
            val local = InetSocketAddress(query.sourceAddress, query.sourcePort)
            val remote = InetSocketAddress(query.destAddress, query.destPort)
            val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
            if (uid <= 0) return false // includes android.os.Process.INVALID_UID (-1)

            val packageName = uidToPackageCache.getOrPut(uid) {
                runCatching { packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
            }
            blocklist.isAppWhitelisted(packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun writeBlockedResponse(output: FileOutputStream, query: DnsPacket.ParsedQuery) {
        try {
            val response = DnsPacket.buildBlockedResponse(query)
            synchronized(output) { output.write(response) }
        } catch (_: Exception) {
            // Dropping the query is an acceptable fallback: the requesting app
            // simply sees the DNS lookup time out, same net effect as blocked.
        }
    }

    /** Writes a [DnsCache] hit back to the app, re-stamped with THIS query's transaction ID
     *  (the cached bytes were captured under whichever query first populated that cache entry). */
    private fun writeCachedResponse(output: FileOutputStream, query: DnsPacket.ParsedQuery, cachedMessage: ByteArray) {
        try {
            val restamped = DnsPacket.withTransactionId(cachedMessage, query.dnsTransactionId)
            val responsePacket = DnsPacket.wrapUpstreamReply(query, restamped)
            synchronized(output) { output.write(responsePacket) }
        } catch (_: Exception) {
            // Fall back to a real upstream forward rather than dropping the query outright.
            forwardExecutor.execute { forwardToUpstream(query, output) }
        }
    }

    private fun forwardToUpstream(query: DnsPacket.ParsedQuery, output: FileOutputStream) {
        try {
            val socket = getOrCreateUpstreamSocket()
            val dnsRequest = buildForwardedRequest(query)
            val replyBuf = ByteArray(1500)

            // Try each configured resolver in order; if the first one (e.g.
            // Cloudflare) times out or is unreachable, fall back to the next
            // (e.g. Google) instead of just dropping the query. A dropped
            // query looks identical to a blocked one from the requesting
            // app's point of view — this is what stops a flaky/blocked
            // upstream from masquerading as false-positive ad-blocking.
            // Same socket is reused across resolver attempts within one
            // query, exactly as before this pooling change — only the
            // socket's LIFETIME changed (now spans many queries), not the
            // per-query fallback sequence.
            for (server in Constants.UPSTREAM_DNS_SERVERS) {
                try {
                    val upstream = InetSocketAddress(server, Constants.DNS_PORT)
                    socket.send(DatagramPacket(dnsRequest, dnsRequest.size, upstream))

                    val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                    socket.receive(replyPacket)

                    val upstreamMessage = replyBuf.copyOf(replyPacket.length)
                    val responsePacket = DnsPacket.wrapUpstreamReply(query, upstreamMessage)
                    synchronized(output) { output.write(responsePacket) }

                    // v3.7.0: cache positive answers only (extractCacheableTtlSeconds
                    // returns null for non-zero RCODE / zero answers) so a real
                    // NXDOMAIN or SERVFAIL is never masked by a stale cache hit.
                    DnsPacket.extractCacheableTtlSeconds(upstreamMessage)?.let { ttl ->
                        DnsCache.put(query.queryDomain, DnsPacket.qtypeOf(query), upstreamMessage, ttl)
                    }
                    return
                } catch (_: java.net.SocketTimeoutException) {
                    // this resolver didn't answer in time, try the next one
                } catch (_: java.io.IOException) {
                    // network hiccup reaching this resolver, try the next one
                }
            }
            // All configured resolvers failed: drop the query. The
            // requesting app's own DNS client will retry, same as any
            // ordinary network hiccup — this is not a block, just silence.
        } catch (_: Exception) {
            // Socket creation/protect() failure, or the pooled socket ended up
            // in a bad state — discard it so the next query on this thread
            // gets a fresh one instead of repeatedly failing on a broken socket.
            discardUpstreamSocket()
        }
    }

    /** Returns this worker thread's persistent upstream socket, creating +
     *  protect()ing a fresh one if this thread doesn't have one yet (or its
     *  previous one was closed/discarded after an error). */
    private fun getOrCreateUpstreamSocket(): DatagramSocket {
        val existing = upstreamSocket.get()
        if (existing != null && !existing.isClosed) return existing

        val socket = DatagramSocket()
        protect(socket) // exclude this socket from the VPN's own routing (avoid loop)
        socket.soTimeout = 2500
        upstreamSocket.set(socket)
        openUpstreamSockets.add(socket)
        return socket
    }

    private fun discardUpstreamSocket() {
        upstreamSocket.get()?.let { socket ->
            runCatching { socket.close() }
            openUpstreamSockets.remove(socket)
        }
        upstreamSocket.remove()
    }

    /** Closes every pooled upstream socket across all forwardExecutor worker
     *  threads. Called from stopVpn() so sockets don't sit open for the
     *  lifetime of the (never-shutdown) executor threads after protection
     *  is turned off — getOrCreateUpstreamSocket() transparently makes a
     *  fresh one next time a thread needs it (e.g. after the VPN restarts). */
    private fun closeUpstreamSockets() {
        openUpstreamSockets.forEach { runCatching { it.close() } }
        openUpstreamSockets.clear()
    }

    private fun buildForwardedRequest(query: DnsPacket.ParsedQuery): ByteArray {
        // Reconstruct just the DNS message (header ID + standard flags + question)
        val buf = java.nio.ByteBuffer.allocate(12 + query.rawDnsQuestionSection.size)
        buf.put(query.dnsTransactionId)
        buf.putShort(0x0100) // standard query, recursion desired
        buf.putShort(1); buf.putShort(0); buf.putShort(0); buf.putShort(0)
        buf.put(query.rawDnsQuestionSection)
        return buf.array().copyOf(buf.position())
    }

    private fun logAndCount(domain: String, blocked: Boolean) {
        serviceScope.launch {
            if (blocked) settingsRepository.incrementBlocked() else settingsRepository.incrementAllowed()
            if (settingsRepository.loggingEnabled.firstValue()) {
                runCatching {
                    AppDatabase.getInstance(applicationContext).domainLogDao()
                        .insert(DomainLogEntity(domain = domain, blocked = blocked))
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(getString(R.string.notif_text_active))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The app was swiped away from Recents. START_STICKY + a foreground
        // service already keeps this service alive under stock Android; this
        // watchdog alarm is a defensive backup for OEM skins that ignore that
        // guarantee and kill foreground services anyway.
        if (running.get()) {
            scheduleWatchdog()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleWatchdog() {
        try {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, Intent(this, RestartReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerAt = SystemClock.elapsedRealtime() + 3000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: Exception) {
            // Best-effort watchdog; normal START_STICKY still applies if this fails.
        }
    }

    override fun onRevoke() {
        // User revoked the VPN permission from system settings.
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.fdzaki.adshield.action.START"
        const val ACTION_STOP = "com.fdzaki.adshield.action.STOP"
        /** True when this STOP is only the "turn off" half of switching to
         *  the other protection mode, not a standalone user stop. */
        const val EXTRA_MODE_SWITCH = "com.fdzaki.adshield.extra.MODE_SWITCH"

        // Companion-level (not instance) state so MainViewModel/Diagnostics
        // screen can observe it without binding to the service — mirrors the
        // same pattern WarpTunnelManager already uses for its lastError.
        // Previously DNS-mode failures (e.g. establish() throwing or
        // returning null) were swallowed silently with no user-visible
        // signal at all, unlike WARP which always had lastError.
        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError

        /** DNS QTYPE A (IPv4 host address) — used by the prefetch pass, which only ever asks for A records. */
        private const val DNS_QTYPE_A = 1
    }
}

/** Small helper: read the current value of a Flow without collecting long-term. */
private suspend fun <T> Flow<T>.firstValue(): T = first()
