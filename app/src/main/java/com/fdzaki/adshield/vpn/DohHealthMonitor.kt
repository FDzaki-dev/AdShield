package com.fdzaki.adshield.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight in-memory diagnostic tracker for [DohClient] (v3.25.0 — see
 * PROJECT_STATE.md "Krisis DNS/DoH v3.9.0–v3.11.1" history).
 *
 * That crisis was abandoned in 2026-08 WITHOUT ever identifying a root
 * cause, because every DoH/plain-UDP failure along the way was swallowed
 * by a bare `catch (_: Exception)` with zero visibility into *why* — the
 * only evidence available was "internet doesn't work", which is useless
 * for telling apart a TLS handshake failure, a `protect()` failure, a
 * timeout, an HTTP error from the DoH provider, or the endpoint being
 * unreachable entirely. This object exists purely to answer "why did it
 * fail" the NEXT time DoH is tested on a real device, surfaced in
 * [com.fdzaki.adshield.ui.screens.DiagnosticsScreen] — no logcat/ADB
 * needed, consistent with this project's crash-logger-first debug
 * priority for everything else.
 *
 * Process-lifetime only, intentionally NOT persisted to disk — this is
 * meant to be read immediately after a failed connection attempt within
 * the same app session, not as a historical log across restarts. A
 * dedicated persisted log would be overkill for a diagnostic aid whose
 * whole job is answering "what just happened".
 */
object DohHealthMonitor {

    data class Snapshot(
        val lastSuccessEndpoint: String? = null,
        val lastSuccessAt: Long? = null,
        val lastFailureEndpoint: String? = null,
        val lastFailureReason: String? = null,
        val lastFailureAt: Long? = null,
        val consecutiveFullFailures: Int = 0,
        val lastFellBackToPlainUdp: Boolean = false,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    /** Call when one DoH endpoint answers successfully. */
    fun recordSuccess(endpoint: String) {
        _state.value = _state.value.copy(
            lastSuccessEndpoint = endpoint,
            lastSuccessAt = System.currentTimeMillis(),
            consecutiveFullFailures = 0,
            lastFellBackToPlainUdp = false,
        )
    }

    /** Call when one specific DoH endpoint fails, before trying the next one.
     *  [reason] should be short and specific (exception class + message) —
     *  NOT a generic "gagal", since the whole point is distinguishing
     *  failure modes. */
    fun recordEndpointFailure(endpoint: String, reason: String) {
        _state.value = _state.value.copy(
            lastFailureEndpoint = endpoint,
            lastFailureReason = reason,
            lastFailureAt = System.currentTimeMillis(),
        )
    }

    /** Call when every configured DoH endpoint failed for one query and the
     *  caller is about to fall back to plain UDP:53. */
    fun recordFullFallback() {
        _state.value = _state.value.copy(
            consecutiveFullFailures = _state.value.consecutiveFullFailures + 1,
            lastFellBackToPlainUdp = true,
        )
    }
}
