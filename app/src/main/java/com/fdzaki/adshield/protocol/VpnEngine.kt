package com.fdzaki.adshield.protocol

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for a tunnel engine, implemented by [WarpVpnEngineAdapter] wrapping
 * the existing WireGuard/WARP tunnel ([com.fdzaki.adshield.warp.WarpTunnelManager]).
 * This thin abstraction exists so [state]/[connect]/[disconnect] have a
 * uniform shape the UI can observe, independent of the underlying
 * WireGuard library's own `Tunnel.State`/`Backend` types.
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
}
