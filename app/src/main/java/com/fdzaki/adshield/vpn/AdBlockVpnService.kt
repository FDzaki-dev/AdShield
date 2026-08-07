package com.fdzaki.adshield.vpn

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.fdzaki.adshield.R
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.data.DnsCache
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.util.Constants
import com.fdzaki.adshield.vpn.dns.AppUidWhitelistChecker
import com.fdzaki.adshield.vpn.dns.DnsPacketLoop
import com.fdzaki.adshield.vpn.dns.DnsPrefetcher
import com.fdzaki.adshield.vpn.dns.DnsQueryLogger
import com.fdzaki.adshield.vpn.dns.UpstreamForwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNS Ad-Block VpnService — lifecycle orchestrator ONLY.
 *
 * v3.17.0 (God-Class refactor, see PROJECT_STATE.md): this class used to
 * contain the packet loop, upstream forwarding + socket pooling, prefetch,
 * per-app UID whitelist resolution, notification building, and watchdog
 * scheduling all inline (~600 lines). All of that logic has been extracted
 * verbatim (ZERO behavior change) into the `vpn.dns` package + `vpn/VpnNotificationFactory`
 * + `vpn/VpnWatchdog`. This class now only: builds the tun interface, wires
 * the extracted collaborators together, and owns the two executors +
 * CoroutineScope + companion-level public API that the rest of the app
 * (MainActivity, QS tiles, BootReceiver, RestartReceiver, MainViewModel)
 * already depends on — that public API (ACTION_START/ACTION_STOP/
 * EXTRA_MODE_SWITCH/lastError) is UNCHANGED, so no other file needed to
 * change for this refactor.
 */
class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)

    // IMPORTANT (fixed 2026-08-03, see PROJECT_STATE.md incident log):
    // the packet-read loop below runs forever on its own dedicated thread
    // and must NEVER share a thread pool with the upstream forwarder. A
    // single shared single-thread executor previously caused every
    // forwarded query to queue behind the infinite loop and never
    // actually run — meaning no non-blocked domain could ever resolve
    // while DNS mode was on.
    private val loopExecutor = Executors.newSingleThreadExecutor()
    private val forwardExecutor = Executors.newFixedThreadPool(4)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var settingsRepository: SettingsRepository
    private val blocklist = BlocklistManager.getInstance()

    private lateinit var whitelistChecker: AppUidWhitelistChecker
    private lateinit var forwarder: UpstreamForwarder
    private lateinit var prefetcher: DnsPrefetcher
    private lateinit var queryLogger: DnsQueryLogger
    private lateinit var packetLoop: DnsPacketLoop

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)

        whitelistChecker = AppUidWhitelistChecker(this, blocklist)
        forwarder = UpstreamForwarder(this)
        prefetcher = DnsPrefetcher(this, serviceScope) { running.get() }
        queryLogger = DnsQueryLogger(serviceScope, settingsRepository, applicationContext)
        packetLoop = DnsPacketLoop(
            blocklist = blocklist,
            whitelistChecker = whitelistChecker,
            forwarder = forwarder,
            forwardExecutor = forwardExecutor,
            isRunning = { running.get() },
            onQueryHandled = { domain, blocked -> queryLogger.log(domain, blocked) },
        )
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

        whitelistChecker.clearCache()
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

        // v3.26.0 — ROOT CAUSE FIX untuk "Krisis DNS/DoH" (lihat PROJECT_STATE.md,
        // dibuktikan lewat log Diagnostik device asli 2026-08-07): app ini TIDAK
        // PERNAH mengecualikan dirinya sendiri dari VPN yang dibuatnya sendiri.
        // addDnsServer(VPN_ADDRESS) + addRoute(VPN_ADDRESS, 32) di atas membuat
        // SEMUA resolusi hostname sistem (termasuk milik proses app ini sendiri)
        // ikut diarahkan ke DNS server palsu (10.111.222.1) itu. DohClient
        // (protocol/DohClient.kt) resolve endpoint-nya lewat HOSTNAME
        // ("cloudflare-dns.com"/"dns.google") — `VpnService.protect()` di dalamnya
        // cuma melindungi SOCKET data, BUKAN langkah resolusi hostname sistem yang
        // terjadi SEBELUM socket itu ada. Akibatnya: proses resolve hostname DoH
        // milik app sendiri ikut masuk tun, nyasar balik ke packet-loop ad-block
        // app sendiri (self-referential), dan gagal dengan persis gejala yang
        // dilaporkan Diagnostik: "UnknownHostException: Unable to resolve host
        // dns.google: No address associated with hostname", 24x fallback beruntun.
        // Fix: kecualikan package sendiri dari VPN ini via
        // addDisallowedApplication — pola standar SETIAP app VPN Android untuk
        // menghindari persis masalah ini (traffic/DNS milik app sendiri lewat
        // jalur network asli, bukan tun buatannya sendiri). WARP
        // (WarpForegroundService, VPN terpisah lewat backend WireGuard) TIDAK
        // terdampak/tidak diubah — ini murni Builder milik AdBlockVpnService.
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // Tidak realistis terjadi (package sendiri selalu ada) — kalau toh
            // gagal, lanjutkan tanpa exclude daripada gagal total membuat VPN;
            // DoH/UDP forwarder masih punya protect() sebagai lapis proteksi
            // kedua untuk socket data (walau tidak menutup celah resolusi
            // hostname yang jadi root cause di atas).
            _lastError.value = "Peringatan: gagal mengecualikan app sendiri dari VPN (${e.message})"
        }

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
        startForeground(Constants.NOTIF_ID, VpnNotificationFactory.build(this))
        loopExecutor.execute { packetLoop.run(iface) }
        prefetcher.start()
    }

    private fun stopVpn(isModeSwitch: Boolean = false) {
        running.set(false)
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        forwarder.closeAllSockets()
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The app was swiped away from Recents. START_STICKY + a foreground
        // service already keeps this service alive under stock Android; this
        // watchdog alarm is a defensive backup for OEM skins that ignore that
        // guarantee and kill foreground services anyway.
        if (running.get()) {
            VpnWatchdog.schedule(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onRevoke() {
        // User revoked the VPN permission from system settings.
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        // v3.16.8 — Concurrency & Lifecycle audit: loopExecutor/forwardExecutor were never
        // shut down here. Both are created fresh in onCreate() every time this Service
        // restarts (each DNS-mode toggle off->on is a NEW instance), but nothing ever called
        // shutdown() on the OLD instance's executors — ThreadPoolExecutor worker threads
        // don't self-terminate just because the Service that created them was destroyed and
        // unreferenced; ExecutorService.shutdown() is the only thing that stops them. Left
        // unfixed, every toggle leaked 5 live non-daemon threads (1 loopExecutor +
        // 4 forwardExecutor) that sat idle forever, accumulating for the life of the process.
        // serviceScope itself is left running (not cancelled) deliberately: its coroutines
        // (settingsRepository writes, prefetchPopularDomains) all already self-terminate via
        // running.get() checks within one loop iteration of stopVpn() above, so cancelling
        // the Job here would risk truncating an in-flight settingsRepository write (e.g.
        // setWasRunning(false)) with no corresponding benefit — the thread pools were the
        // actual leak, not the Job.
        loopExecutor.shutdownNow()
        forwardExecutor.shutdown()
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
