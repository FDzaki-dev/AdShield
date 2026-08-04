package com.fdzaki.adshield.qs

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.fdzaki.adshield.MainActivity
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.vpn.AdBlockVpnService
import com.fdzaki.adshield.warp.WarpForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Quick Settings tile untuk mode VPN Tunnel WARP (v3.8.0) — pasangan
 * DnsTileService, kontrak & alasan desain SAMA PERSIS (lihat komentar di
 * DnsTileService.kt), cuma arah mode-nya dibalik.
 */
class WarpTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var listeningJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.IO + Job())
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        val activeScope = scope ?: return
        listeningJob?.cancel()
        listeningJob = SettingsRepository(applicationContext).activeMode
            .onEach { mode ->
                qsTile?.apply {
                    state = if (mode == AppMode.WARP_TUNNEL) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
            .launchIn(activeScope)
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val activeScope = scope ?: CoroutineScope(Dispatchers.IO + Job()).also { scope = it }
        activeScope.launch {
            val mode = SettingsRepository(applicationContext).activeMode.first()

            if (mode == AppMode.WARP_TUNNEL) {
                stopWarp(isModeSwitch = false)
                return@launch
            }

            if (mode == AppMode.DNS_ADBLOCK) {
                stopDns(isModeSwitch = true)
            }

            if (VpnService.prepare(applicationContext) == null) {
                val intent = Intent(applicationContext, WarpForegroundService::class.java)
                    .setAction(WarpForegroundService.ACTION_START)
                ContextCompat.startForegroundService(applicationContext, intent)
            } else {
                launchPermissionActivity()
            }
        }
    }

    private fun stopWarp(isModeSwitch: Boolean) {
        val intent = Intent(applicationContext, WarpForegroundService::class.java)
            .setAction(WarpForegroundService.ACTION_STOP)
            .putExtra(WarpForegroundService.EXTRA_MODE_SWITCH, isModeSwitch)
        startService(intent)
    }

    private fun stopDns(isModeSwitch: Boolean) {
        val intent = Intent(applicationContext, AdBlockVpnService::class.java)
            .setAction(AdBlockVpnService.ACTION_STOP)
            .putExtra(AdBlockVpnService.EXTRA_MODE_SWITCH, isModeSwitch)
        startService(intent)
    }

    private fun launchPermissionActivity() {
        val activityIntent = Intent(applicationContext, MainActivity::class.java)
            .setAction(ACTION_REQUEST_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                applicationContext, REQUEST_CODE, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(activityIntent)
        }
    }

    companion object {
        const val ACTION_REQUEST_PERMISSION = "com.fdzaki.adshield.qs.action.REQUEST_PERMISSION_WARP"
        private const val REQUEST_CODE = 3802
    }
}
