package com.fdzaki.adshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.vpn.AdBlockVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(context.applicationContext)
                val shouldAutoStart = repo.autoStartOnBoot.first()
                val wasRunning = repo.wasRunning.first()

                // VpnService.prepare() returning null means the user already
                // granted this app VPN permission previously, so we can start
                // it without any UI prompt.
                if (shouldAutoStart && wasRunning && VpnService.prepare(context) == null) {
                    val serviceIntent = Intent(context, AdBlockVpnService::class.java)
                        .setAction(AdBlockVpnService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
