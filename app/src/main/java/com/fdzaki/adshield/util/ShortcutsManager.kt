package com.fdzaki.adshield.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.fdzaki.adshield.MainActivity
import com.fdzaki.adshield.R

/**
 * Owns the two DYNAMIC launcher shortcuts — "Nyalakan/Matikan DNS" and
 * "Nyalakan/Matikan WARP" — whose label and icon depend on which of the two
 * mutually-exclusive modes (AppMode) is currently running.
 *
 * Called from AdShieldApp, which collects SettingsRepository.activeMode for
 * the whole app process lifetime and calls updateToggleShortcuts() on every
 * change. This keeps the shortcuts correct even when the mode was toggled
 * from the Home screen UI, not from a shortcut itself.
 *
 * The two STATIC shortcuts (Whitelist, Log) are separate — declared once in
 * res/xml/shortcuts.xml since their label never changes — and are not
 * touched by this object.
 */
object ShortcutsManager {

    const val ACTION_TOGGLE_DNS = "com.fdzaki.adshield.shortcut.TOGGLE_DNS"
    const val ACTION_TOGGLE_WARP = "com.fdzaki.adshield.shortcut.TOGGLE_WARP"
    const val EXTRA_SHORTCUT_DEST = "com.fdzaki.adshield.extra.SHORTCUT_DEST"

    private const val ID_TOGGLE_DNS = "toggle_dns"
    private const val ID_TOGGLE_WARP = "toggle_warp"

    fun updateToggleShortcuts(context: Context, activeMode: String) {
        val dnsRunning = activeMode == AppMode.DNS_ADBLOCK
        val warpRunning = activeMode == AppMode.WARP_TUNNEL

        val dnsShortcut = ShortcutInfoCompat.Builder(context, ID_TOGGLE_DNS)
            .setShortLabel(context.getString(if (dnsRunning) R.string.shortcut_dns_off_short else R.string.shortcut_dns_on_short))
            .setLongLabel(context.getString(if (dnsRunning) R.string.shortcut_dns_off_long else R.string.shortcut_dns_on_long))
            .setIcon(
                IconCompat.createWithResource(
                    context,
                    if (dnsRunning) R.drawable.ic_shortcut_dns_off else R.drawable.ic_shortcut_dns_on
                )
            )
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_TOGGLE_DNS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            .build()

        val warpShortcut = ShortcutInfoCompat.Builder(context, ID_TOGGLE_WARP)
            .setShortLabel(context.getString(if (warpRunning) R.string.shortcut_warp_off_short else R.string.shortcut_warp_on_short))
            .setLongLabel(context.getString(if (warpRunning) R.string.shortcut_warp_off_long else R.string.shortcut_warp_on_long))
            .setIcon(
                IconCompat.createWithResource(
                    context,
                    if (warpRunning) R.drawable.ic_shortcut_warp_off else R.drawable.ic_shortcut_warp_on
                )
            )
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_TOGGLE_WARP)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            .build()

        // setDynamicShortcuts REPLACES the whole dynamic set atomically — we
        // always pass both, so this is safe to call on every activeMode
        // change without needing add/update/remove bookkeeping.
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(dnsShortcut, warpShortcut))
        }
    }
}
