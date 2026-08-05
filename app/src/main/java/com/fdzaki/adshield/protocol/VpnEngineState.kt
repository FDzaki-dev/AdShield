package com.fdzaki.adshield.protocol

/**
 * Connection lifecycle state shared by every [VpnEngine] implementation.
 * Mirrors the state names requested in the multi-protocol spec
 * (Connecting/Connected/Disconnected/Reconnecting) plus an explicit Error
 * case carrying a user-facing message — same pattern AdBlockVpnService and
 * WarpTunnelManager already use via their own `lastError` StateFlow, just
 * unified into one type so the UI layer doesn't need per-engine handling.
 */
sealed class VpnEngineState {
    data object Disconnected : VpnEngineState()
    data object Connecting : VpnEngineState()
    data class Connected(
        /** Epoch millis when the tunnel came up — for duration display in the notification. */
        val connectedSinceMs: Long,
    ) : VpnEngineState()
    data class Reconnecting(val attempt: Int) : VpnEngineState()
    data class Error(val message: String) : VpnEngineState()
}
