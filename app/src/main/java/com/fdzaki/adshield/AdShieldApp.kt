package com.fdzaki.adshield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.util.Constants
import com.fdzaki.adshield.util.CrashLogger
import com.fdzaki.adshield.util.ShortcutsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdShieldApp : Application() {

    /** Process-wide scope for lightweight background sync that must outlive
     *  any single Activity/ViewModel — keeping the launcher's toggle
     *  shortcuts (Nyalakan/Matikan DNS & WARP) in sync with activeMode even
     *  when MainActivity isn't currently open (e.g. user toggled a mode,
     *  then killed the app from Recents; shortcut label must still update). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Installed FIRST, before anything else in onCreate, so that any
        // crash during the rest of startup (notification channel creation,
        // shortcut sync, etc.) is also captured.
        CrashLogger.install(this)
        createNotificationChannel()
        syncToggleShortcutsWithActiveMode()
    }

    private fun syncToggleShortcutsWithActiveMode() {
        val settingsRepository = SettingsRepository(this)
        appScope.launch {
            settingsRepository.activeMode.collect { mode ->
                ShortcutsManager.updateToggleShortcuts(this@AdShieldApp, mode)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                Constants.NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status koneksi & statistik pemblokiran AdShield"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
