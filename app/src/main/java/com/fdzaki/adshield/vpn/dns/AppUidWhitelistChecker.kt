package com.fdzaki.adshield.vpn.dns

import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.system.OsConstants
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.vpn.DnsPacket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves which installed app actually sent a given DNS query (via
 * ConnectivityManager.getConnectionOwnerUid, API 29+) and checks it against
 * the user's per-app whitelist. Extracted from AdBlockVpnService (v3.17.0
 * God-Class refactor, see PROJECT_STATE.md) — ZERO behavior change, same
 * API-level guard, same UID->package cache semantics.
 *
 * Below API 29 [isFromWhitelistedApp] silently returns false — per-app
 * whitelist just has no effect on older Android, since the OS doesn't
 * expose this attribution API there (see keputusan arsitektur #5 in
 * PROJECT_STATE.md — this is documented in UI, not a silent bug).
 */
class AppUidWhitelistChecker(
    private val vpnService: VpnService,
    private val blocklist: BlocklistManager,
) {

    // Small cache so repeated queries from the same app don't re-hit
    // PackageManager every time; cleared each time the VPN restarts.
    private val uidToPackageCache = ConcurrentHashMap<Int, String?>()

    fun isFromWhitelistedApp(query: DnsPacket.ParsedQuery): Boolean {
        val packageName = resolvePackageName(query) ?: return false
        return blocklist.isAppWhitelisted(packageName)
    }

    // v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md): extracted the
    // uid->package resolution out of isFromWhitelistedApp (ZERO behavior
    // change for the whitelist path) so DnsPacketLoop can also call it
    // directly to attribute a query to an app when the screen is off,
    // without duplicating the getConnectionOwnerUid + cache logic.
    fun resolvePackageName(query: DnsPacket.ParsedQuery): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val cm = vpnService.getSystemService(ConnectivityManager::class.java) ?: return null
            val local = InetSocketAddress(query.sourceAddress, query.sourcePort)
            val remote = InetSocketAddress(query.destAddress, query.destPort)
            val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
            if (uid <= 0) return null // includes android.os.Process.INVALID_UID (-1)

            uidToPackageCache.getOrPut(uid) {
                runCatching { vpnService.packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Must be called on every VPN (re)start — a UID can be reassigned to a
     *  different app across app installs/uninstalls between VPN sessions. */
    fun clearCache() {
        uidToPackageCache.clear()
    }
}
