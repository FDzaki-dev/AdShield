package com.fdzaki.adshield.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fdzaki.adshield.MainActivity
import com.fdzaki.adshield.R
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.util.ShortcutsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared logic for the two Quick Settings tiles (DNS Ad-Block, WARP).
 *
 * Tapping a tile does NOT start/stop the VPN service directly — it opens
 * MainActivity with the exact same ACTION_TOGGLE_DNS/ACTION_TOGGLE_WARP
 * intent the launcher shortcuts already use (see ShortcutsManager and
 * MainActivity.handleShortcutToggleIntent), so the mutual-exclusion
 * start/stop logic lives in exactly ONE place — PROJECT_STATE.md decision
 * #6 explicitly requires this for any new mode-toggle entry point. A brief
 * flash of MainActivity is an intentional trade-off, not an oversight:
 * VpnService.prepare() needs an Activity for the first-time consent
 * dialog, so a truly silent background toggle isn't reliably possible from
 * a TileService regardless of how it's written.
 *
 * Only reacts in [onStartListening] (a "passive" tile, not an active-polling
 * one) — correct enough for this app's usage pattern (user pulls down QS
 * panel, tile refreshes then) without the added complexity/battery cost of
 * android:meta-data ACTIVE_TILE + onTileAdded polling.
 *
 * NOT `private` — Kotlin forbids a public class (DnsAdBlockTileService /
 * WarpTunnelTileService below, both public so the Android system can
 * instantiate them via reflection from AndroidManifest.xml) from extending
 * a supertype with narrower visibility ("public subclass exposes its
 * private-in-file supertype"). Package-visibility default is intentional
 * here, don't add `private` back.
 */
abstract class BaseAdShieldTileService(
    private val targetMode: String,
    private val toggleAction: String,
    private val pendingIntentRequestCode: Int,
    @DrawableRes private val iconRes: Int,
    @StringRes private val onLabelRes: Int,
    @StringRes private val offLabelRes: Int
) : TileService() {

    private val tileJob = Job()
    private val tileScope = CoroutineScope(Dispatchers.Main + tileJob)

    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch {
            val activeMode = SettingsRepository(applicationContext).activeMode.first()
            applyState(running = activeMode == targetMode)
        }
    }

    private fun applyState(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, iconRes)
        // Label shows the ACTION the tap performs (matches the launcher
        // shortcuts' convention) — "Matikan DNS" while running, "Nyalakan
        // DNS" while off — not the current status as a noun.
        tile.label = getString(if (running) offLabelRes else onLabelRes)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(toggleAction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // TileService.startActivityAndCollapse(Intent) THROWS
        // UnsupportedOperationException on Android 14+ (API 34) for apps
        // targeting SDK 34+ (this app does — see app/build.gradle.kts) —
        // MUST use the PendingIntent overload there. The PendingIntent
        // overload doesn't exist below API 34, so this branch is not
        // optional/defensive, both paths are load-bearing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                pendingIntentRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileJob.cancel()
    }
}

class DnsAdBlockTileService : BaseAdShieldTileService(
    targetMode = AppMode.DNS_ADBLOCK,
    toggleAction = ShortcutsManager.ACTION_TOGGLE_DNS,
    pendingIntentRequestCode = 1001,
    iconRes = R.drawable.ic_tile_dns,
    onLabelRes = R.string.shortcut_dns_on_short,
    offLabelRes = R.string.shortcut_dns_off_short
)

class WarpTunnelTileService : BaseAdShieldTileService(
    targetMode = AppMode.WARP_TUNNEL,
    toggleAction = ShortcutsManager.ACTION_TOGGLE_WARP,
    pendingIntentRequestCode = 1002,
    iconRes = R.drawable.ic_tile_warp,
    onLabelRes = R.string.shortcut_warp_on_short,
    offLabelRes = R.string.shortcut_warp_off_short
)
