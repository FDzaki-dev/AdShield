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

        serviceScope.launch {
            blocklist.loadDefaultList(applicationContext)
            blocklist.loadCachedRemoteListIfPresent(applicationContext)
            blocklist.setCustomBlocked(settingsRepository.customBlockedDomains.firstValue())
            blocklist.setCustomAllowed(settingsRepository.customAllowedDomains.firstValue())
            blocklist.setWhitelistedApps(settingsRepository.whitelistedApps.firstValue())
            settingsRepository.setWasRunning(true)
            settingsRepository.setActiveMode(com.fdzaki.adshield.util.AppMode.DNS_ADBLOCK)
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
            return
        }
        _lastError.value = null
        running.set(true)
        startForeground(Constants.NOTIF_ID, buildNotification())
        loopExecutor.execute { runPacketLoop(iface) }
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
                // Fire-and-forget async forward so a slow upstream lookup never
                // stalls the packet loop for other concurrent queries. Must use
                // forwardExecutor (NOT loopExecutor) — see field comment above.
                forwardExecutor.execute { forwardToUpstream(query, output) }
                logAndCount(query.queryDomain, false)
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

                    val responsePacket = DnsPacket.wrapUpstreamReply(
                        query, replyBuf.copyOf(replyPacket.length)
                    )
                    synchronized(output) { output.write(responsePacket) }
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
    }
}

/** Small helper: read the current value of a Flow without collecting long-term. */
private suspend fun <T> Flow<T>.firstValue(): T = first()
