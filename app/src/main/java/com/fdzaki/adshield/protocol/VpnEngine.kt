package com.fdzaki.adshield.protocol

import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract every VPN protocol engine (WireGuard/WARP, OpenVPN, IKEv2,
 * Shadowsocks/VLESS) implements, so the rest of the app (UI, QS tiles,
 * BootReceiver, watchdog receivers) can drive any of them the same way
 * without protocol-specific branching outside this package.
 *
 * v3.12.0 (see PROJECT_STATE.md) — introduced as part of the staged
 * multi-protocol rollout. [WarpTunnelManager] (existing WireGuard engine)
 * is NOT yet adapted to this interface — that is planned as its own batch
 * ("prove the abstraction against the one engine that already works")
 * before any new engine is added behind it. Implementations for
 * OPENVPN/IKEV2/SHADOWSOCKS do not exist yet; only [VpnEngineState] and
 * [VpnProtocolConfig] are defined so far.
 */
interface VpnEngine {

    /** Which [com.fdzaki.adshield.util.AppMode] constant this engine backs. */
    val mode: String

    /** Current connection state, observable by UI/tiles/notification. */
    val state: StateFlow<VpnEngineState>

    /**
     * Starts connecting using the given [config]. Suspends until either
     * connected or a terminal failure is reached — callers should still
     * observe [state] rather than rely solely on this call returning, since
     * some engines may report Connected asynchronously after this returns.
     */
    suspend fun connect(config: VpnProtocolConfig)

    /** Tears down the tunnel, if any, and moves [state] to Disconnected. */
    suspend fun disconnect()

    /**
     * v3.14.0 — some platform engines (IKEv2 via [android.net.VpnManager]) require explicit user
     * consent through a system Activity before [connect] can succeed, the same shape as
     * `VpnService.prepare()` but returned per-call instead of checked once globally, because
     * `VpnManager.provisionVpnProfile()` hands back that consent [android.content.Intent] directly
     * rather than a boolean/exception.
     *
     * Default is null: VpnService-based engines (DNS Ad-Block, WARP) don't need this — their
     * equivalent consent step is `VpnService.prepare()`, already handled by the caller
     * (MainActivity) BEFORE any [VpnEngine] method is invoked, entirely outside this interface.
     * [WarpVpnEngineAdapter] does not override this.
     *
     * Contract: if this returns non-null, the caller MUST launch the Intent (e.g. via an
     * ActivityResultLauncher) and only call [connect] after the user grants consent
     * (`RESULT_OK`). Calling [connect] while consent is outstanding will surface a
     * [VpnEngineState.Error] rather than silently failing.
     */
    suspend fun prepareConsent(config: VpnProtocolConfig): android.content.Intent? = null
}
