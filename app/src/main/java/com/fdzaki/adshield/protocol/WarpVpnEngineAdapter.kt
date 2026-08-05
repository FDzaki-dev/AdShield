package com.fdzaki.adshield.protocol

import android.content.Context
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.warp.WarpTunnelManager
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * v3.13.0 (see PROJECT_STATE.md) — Batch 2 of the staged multi-protocol
 * rollout: "adapt WireGuard/WARP to VpnEngine, prove the abstraction against
 * the engine that already works, before adding any new one behind it."
 *
 * This wraps the EXISTING [WarpTunnelManager] singleton — 0 lines of
 * WarpTunnelManager.kt (or any other file under warp/) were touched to build
 * this. Every quirk documented in WarpTunnelManager's own kdoc/PROJECT_STATE
 * decision #6/#6c/#6d (desiredRunning vs state, trace-probe as the real
 * connectivity signal, backoff/reconnect, MTU 1280 default, etc.) still
 * applies unchanged underneath — this class only translates its existing
 * public surface ([WarpTunnelManager.state] as [Tunnel.State],
 * [WarpTunnelManager.connecting], [WarpTunnelManager.lastError],
 * [WarpTunnelManager.quality]) into the shared [VpnEngine]/[VpnEngineState]
 * shape so callers written against the interface don't need to know any of
 * that WireGuard-specific detail.
 *
 * NOT YET WIRED to any UI/MainActivity/HomeScreen/BootReceiver/QS tile —
 * those all still call [WarpTunnelManager] directly today, and keep doing so
 * until a later batch explicitly migrates them. This class exists purely to
 * prove the interface shape compiles and behaves against a real engine;
 * wiring call sites over is a deliberately separate, later step (Batch Lock —
 * keeps this batch's diff to protocol/ only, 0 risk to the working WARP UI
 * path).
 */
class WarpVpnEngineAdapter(context: Context) : VpnEngine {

    private val manager = WarpTunnelManager.getInstance(context)
    private val adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val mode: String = AppMode.WARP_TUNNEL

    private val _state = MutableStateFlow<VpnEngineState>(VpnEngineState.Disconnected)
    override val state: StateFlow<VpnEngineState> = _state

    // Tracks when the underlying tunnel most recently transitioned into UP, purely for
    // VpnEngineState.Connected.connectedSinceMs — WarpTunnelManager itself doesn't track
    // this (its own UI reads elapsed-since from the foreground service notification instead),
    // so the adapter owns this single piece of extra state rather than adding it upstream.
    @Volatile private var connectedSinceMs: Long = 0L

    init {
        combine(manager.connecting, manager.state, manager.lastError, manager.quality) {
            connecting, tunnelState, lastError, quality ->
            when {
                connecting -> VpnEngineState.Connecting
                tunnelState == Tunnel.State.UP -> {
                    if (connectedSinceMs == 0L) connectedSinceMs = System.currentTimeMillis()
                    VpnEngineState.Connected(connectedSinceMs)
                }
                lastError != null -> {
                    connectedSinceMs = 0L
                    VpnEngineState.Error(lastError)
                }
                quality.reconnectAttempts > 0 -> VpnEngineState.Reconnecting(quality.reconnectAttempts)
                else -> {
                    connectedSinceMs = 0L
                    VpnEngineState.Disconnected
                }
            }
        }.onEach { _state.value = it }.launchIn(adapterScope)
    }

    /**
     * [config] fields beyond [VpnProtocolConfig.Warp.routeIpv6] are currently ignored — see
     * that class's kdoc. routeIpv6 itself is NOT forwarded to WarpTunnelManager here either:
     * the manager reads `SettingsRepository.warpRouteIpv6` directly on every connect()/
     * reconnect() (decision #6e), which is the existing single source of truth the real
     * Home screen toggle already writes to. Threading [config]'s copy through would create a
     * second, competing source of truth for the same setting — left as a later decision if/
     * when a call site actually needs to override it per-call instead of via that toggle.
     */
    override suspend fun connect(config: VpnProtocolConfig) {
        require(config is VpnProtocolConfig.Warp) {
            "WarpVpnEngineAdapter only accepts VpnProtocolConfig.Warp, got ${config::class.simpleName}"
        }
        manager.connect()
    }

    override suspend fun disconnect() {
        manager.disconnect()
    }
}
