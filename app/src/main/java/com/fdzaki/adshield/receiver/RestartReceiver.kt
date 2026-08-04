package com.fdzaki.adshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import com.fdzaki.adshield.vpn.AdBlockVpnService

/**
 * Fired by the AlarmManager watchdog scheduled in AdBlockVpnService.onTaskRemoved().
 * Some OEM skins (e.g. XOS/MIUI-style aggressive battery managers) kill
 * foreground services shortly after the task is swiped from Recents despite
 * Android's normal guarantees. This receiver checks a few seconds later and
 * relaunches protection if it was supposed to still be running.
 */
class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (VpnService.prepare(context) != null) return // permission somehow revoked, don't prompt from background

        val serviceIntent = Intent(context, AdBlockVpnService::class.java)
            .setAction(AdBlockVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
