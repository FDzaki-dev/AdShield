package com.fdzaki.adshield.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/**
 * Tracks screen on/off state for the Silent Leak Detector feature (see
 * PROJECT_STATE.md v4.5.0 — flags apps making DNS queries while the screen
 * is off, i.e. background/idle chatter the user never sees).
 *
 * ACTION_SCREEN_ON/OFF are protected system broadcasts — they can ONLY be
 * observed via a DYNAMICALLY registered receiver (Context.registerReceiver
 * at runtime), never declared in AndroidManifest.xml. No manifest change
 * needed for this feature.
 *
 * [isScreenOff] is a plain @Volatile boolean, not a Flow/StateFlow, so it
 * can be read directly from the packet-loop hot path (see DnsPacketLoop)
 * with zero allocation/dispatch cost per query — same idiom as
 * DnsQueryLogger.loggingEnabledCache.
 */
class ScreenStateMonitor(private val context: Context) {

    @Volatile var isScreenOff: Boolean = false
        private set

    private var receiver: BroadcastReceiver? = null

    /** Call once per VPN session start (mirrors DnsQueryLogger.start()) —
     *  seeds the initial value from PowerManager so a session that starts
     *  while the screen is already off is attributed correctly from the
     *  very first query, not just from the next SCREEN_OFF broadcast. */
    fun start() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenOff = pm?.isInteractive == false

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                isScreenOff = intent.action == Intent.ACTION_SCREEN_OFF
            }
        }
        receiver = r
        context.registerReceiver(
            r,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
    }

    /** Must be called from stopVpn() (mirrors DnsQueryLogger.stop()) — this
     *  receiver is created fresh every VPN session in onCreate(), and an
     *  unregistered receiver leaks like any other. */
    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }
}
