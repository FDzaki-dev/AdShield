package com.fdzaki.adshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import com.fdzaki.adshield.warp.WarpForegroundService

/**
 * WARP-mode counterpart of [RestartReceiver] — fired by the watchdog alarm
 * scheduled in WarpForegroundService.onTaskRemoved() to defeat aggressive
 * OEM battery managers that kill foreground services after Recents-swipe.
 */
class WarpRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (VpnService.prepare(context) != null) return // permission somehow revoked

        val serviceIntent = Intent(context, WarpForegroundService::class.java)
            .setAction(WarpForegroundService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
