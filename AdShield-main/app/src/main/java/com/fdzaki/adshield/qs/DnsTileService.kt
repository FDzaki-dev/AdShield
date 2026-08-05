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
 * Quick Settings tile untuk mode Ad-Block DNS (v3.8.0). Toggle LANGSUNG
 * dari panel QS tanpa membuka Activity apa pun, selama izin VPN sudah
 * pernah diberikan sekali — lihat PROJECT_STATE.md keputusan arsitektur
 * #13. Pola instansiasi SettingsRepository/start-service meniru persis
 * BootReceiver (komponen non-Activity, applicationContext langsung, TIDAK
 * lewat MainViewModel karena TileService bukan LifecycleOwner).
 *
 * Mutual exclusion dengan WARP ditegakkan manual di sini (stop WARP
 * dengan EXTRA_MODE_SWITCH=true sebelum start DNS) karena
 * MainActivity.startDnsService()/startWarpService() tidak bisa dipanggil
 * dari TileService.
 *
 * Kalau izin VPN belum pernah diberikan (atau dicabut), dialog konsen
 * sistem itu WAJIB lewat Activity — batasan OS VpnService, bukan pilihan
 * desain. Untuk kasus itu SATU-SATUNYA saat tile ini membuka MainActivity,
 * lewat ACTION_REQUEST_PERMISSION, dan MainActivity langsung finish()
 * begitu dialog selesai — lihat MainActivity.handleTilePermissionIntent().
 */
class DnsTileService : TileService() {

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
                    state = if (mode == AppMode.DNS_ADBLOCK) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
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

            if (mode == AppMode.DNS_ADBLOCK) {
                // Sudah nyala -> matikan langsung, tidak butuh cek izin apa pun untuk stop.
                stopDns(isModeSwitch = false)
                return@launch
            }

            if (mode == AppMode.WARP_TUNNEL) {
                stopWarp(isModeSwitch = true)
            }

            if (VpnService.prepare(applicationContext) == null) {
                // Izin sudah pernah diberikan -> langsung start di background, TANPA
                // Activity sama sekali. TileService#onClick() adalah salah satu
                // exemption resmi Android untuk start foreground service dari
                // background (lihat dokumentasi ForegroundServiceStartNotAllowedException).
                val intent = Intent(applicationContext, AdBlockVpnService::class.java)
                    .setAction(AdBlockVpnService.ACTION_START)
                ContextCompat.startForegroundService(applicationContext, intent)
            } else {
                launchPermissionActivity()
            }
        }
    }

    private fun stopDns(isModeSwitch: Boolean) {
        val intent = Intent(applicationContext, AdBlockVpnService::class.java)
            .setAction(AdBlockVpnService.ACTION_STOP)
            .putExtra(AdBlockVpnService.EXTRA_MODE_SWITCH, isModeSwitch)
        startService(intent)
    }

    private fun stopWarp(isModeSwitch: Boolean) {
        val intent = Intent(applicationContext, WarpForegroundService::class.java)
            .setAction(WarpForegroundService.ACTION_STOP)
            .putExtra(WarpForegroundService.EXTRA_MODE_SWITCH, isModeSwitch)
        startService(intent)
    }

    private fun launchPermissionActivity() {
        val activityIntent = Intent(applicationContext, MainActivity::class.java)
            .setAction(ACTION_REQUEST_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // startActivityAndCollapse(Intent) is non-functional on Android 14+;
            // the PendingIntent overload (added API 34) is required there.
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
        const val ACTION_REQUEST_PERMISSION = "com.fdzaki.adshield.qs.action.REQUEST_PERMISSION_DNS"
        private const val REQUEST_CODE = 3801
    }
}
