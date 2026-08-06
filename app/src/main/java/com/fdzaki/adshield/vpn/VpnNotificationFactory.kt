package com.fdzaki.adshield.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fdzaki.adshield.MainActivity
import com.fdzaki.adshield.R
import com.fdzaki.adshield.util.Constants

/**
 * Builds the persistent foreground-service notification for DNS Ad-Block
 * mode. Extracted from AdBlockVpnService (v3.17.0 God-Class refactor, see
 * PROJECT_STATE.md) — ZERO behavior change from the pre-refactor inline
 * `buildNotification()`.
 */
object VpnNotificationFactory {

    fun build(context: Context): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            context, 0, Intent(context, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_title_active))
            .setContentText(context.getString(R.string.notif_text_active))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
