package com.fdzaki.adshield.vpn

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.fdzaki.adshield.receiver.RestartReceiver

/**
 * Defensive AlarmManager watchdog for OEM skins that kill foreground
 * services on task-swipe despite START_STICKY. Extracted from
 * AdBlockVpnService (v3.17.0 God-Class refactor, see PROJECT_STATE.md) —
 * ZERO behavior change from the pre-refactor inline `scheduleWatchdog()`.
 */
object VpnWatchdog {

    fun schedule(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(context, RestartReceiver::class.java),
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
}
