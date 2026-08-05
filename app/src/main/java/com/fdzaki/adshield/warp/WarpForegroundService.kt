package com.fdzaki.adshield.warp

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fdzaki.adshield.MainActivity
import com.fdzaki.adshield.R
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.protocol.VpnEngineState
import com.fdzaki.adshield.protocol.VpnProtocolConfig
import com.fdzaki.adshield.protocol.WarpVpnEngineAdapter
import com.fdzaki.adshield.receiver.WarpRestartReceiver
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.util.Constants
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Thin foreground-service wrapper around [WarpTunnelManager]. Its only jobs
 * are: (1) keep a persistent notification so Android doesn't treat this as
 * a background-only process, and (2) survive Recents-swipe the same way
 * AdBlockVpnService does (watchdog alarm). The actual VPN interface is
 * owned by the WireGuard library's own `GoBackend.VpnService` — this class
 * never touches raw sockets/packets itself.
 *
 * v3.15.0 (see PROJECT_STATE.md) — Batch "wire WARP to VpnEngine": this is
 * the ONLY real drive point for WARP lifecycle in the whole app (MainActivity/
 * HomeScreen/BootReceiver/WarpTileService all just send Intents here, never
 * touch [WarpTunnelManager] directly), so connect()/disconnect() below now go
 * through [WarpVpnEngineAdapter] instead of [tunnelManager] directly — proves
 * the VpnEngine abstraction actually drives production traffic, not just a
 * standalone class that compiles. [tunnelManager] itself is KEPT (not
 * removed) purely for [observeQualityForNotification]/[buildNotification],
 * which need [WarpConnectionQuality]/[Tunnel.State] detail that
 * [VpnEngineState] deliberately does not carry — both objects wrap the SAME
 * underlying [WarpTunnelManager.getInstance] singleton, so this is not two
 * competing sources of truth, just two views of one.
 */
class WarpForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var tunnelManager: WarpTunnelManager
    private lateinit var warpEngine: WarpVpnEngineAdapter
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        tunnelManager = WarpTunnelManager.getInstance(applicationContext)
        warpEngine = WarpVpnEngineAdapter(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val isModeSwitch = intent.getBooleanExtra(EXTRA_MODE_SWITCH, false)
                scope.launch {
                    warpEngine.disconnect()
                    // See AdBlockVpnService.stopVpn() for why: during a
                    // WARP->DNS switch, AdBlockVpnService is about to write
                    // DNS_ADBLOCK from its own coroutine — writing NONE here
                    // too would race it and could leave DNS_ADBLOCK
                    // clobbered back to NONE after reboot.
                    if (!isModeSwitch) {
                        settingsRepository.setActiveMode(AppMode.NONE)
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                startForeground(Constants.WARP_NOTIF_ID, buildNotification(Tunnel.State.DOWN, null))
                observeQualityForNotification()
                scope.launch {
                    // VpnEngine.connect() returns Unit (not a success Boolean like
                    // WarpTunnelManager.connect() did) — by design (see VpnEngine.kt kdoc),
                    // callers observe state instead. First terminal state after connect()
                    // returns is either Connected or Error; Reconnecting/Disconnected are not
                    // treated as "success" here on this very first attempt.
                    val routeIpv6 = settingsRepository.warpRouteIpv6.first()
                    warpEngine.connect(VpnProtocolConfig.Warp(routeIpv6 = routeIpv6))
                    val result = warpEngine.state.first {
                        it is VpnEngineState.Connected || it is VpnEngineState.Error
                    }
                    if (result is VpnEngineState.Connected) {
                        settingsRepository.setActiveMode(AppMode.WARP_TUNNEL)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleWatchdog()
        super.onTaskRemoved(rootIntent)
    }

    private fun scheduleWatchdog() {
        try {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                this, 1, Intent(this, WarpRestartReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerAt = SystemClock.elapsedRealtime() + 3000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: Exception) {
            // Best-effort watchdog only.
        }
    }

    /** Refreshes the persistent notification whenever tunnel state or connection quality
     *  changes, so the user can see latency/reconnect status without opening the app. */
    private fun observeQualityForNotification() {
        scope.launch {
            combine(tunnelManager.state, tunnelManager.quality) { state, quality -> state to quality }
                .collect { (state, quality) ->
                    runCatching {
                        NotificationManagerCompat.from(this@WarpForegroundService)
                            .notify(Constants.WARP_NOTIF_ID, buildNotification(state, quality))
                    }
                }
        }
    }

    private fun buildNotification(state: Tunnel.State, quality: WarpConnectionQuality?): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, WarpForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentText = when {
            state != Tunnel.State.UP -> "Menyambungkan ke Cloudflare WARP…"
            quality == null || quality.lastCheckedAt == 0L -> "Terhubung — memeriksa kualitas jalur…"
            quality.reconnectAttempts > 0 -> "Menyambung ulang (percobaan ke-${quality.reconnectAttempts})…"
            quality.trafficConfirmed -> "Aktif • ${quality.latencyMs} ms lewat Cloudflare WARP"
            else -> "Terhubung, tapi trafik belum terkonfirmasi lewat WARP"
        }
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
            .setContentTitle("VPN Tunnel (WARP) aktif")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        scope.launch { warpEngine.disconnect() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.fdzaki.adshield.warp.action.START"
        const val ACTION_STOP = "com.fdzaki.adshield.warp.action.STOP"
        /** True when this STOP is only the "turn off" half of switching to
         *  the other protection mode, not a standalone user stop. */
        const val EXTRA_MODE_SWITCH = "com.fdzaki.adshield.warp.extra.MODE_SWITCH"
    }
}
