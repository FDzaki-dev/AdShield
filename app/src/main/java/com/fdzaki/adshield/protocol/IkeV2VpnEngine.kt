package com.fdzaki.adshield.protocol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Ikev2VpnProfile
import android.net.VpnManager
import android.net.VpnProfileState
import android.os.Build
import androidx.annotation.RequiresApi
import com.fdzaki.adshield.util.AppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * v3.14.0 (see PROJECT_STATE.md) — Batch 3/N of the staged multi-protocol rollout, picked over
 * OpenVPN after research found NO non-GPL/AGPL path exists for OpenVPN on Android (both
 * `ics-openvpn`, GPLv2, and OpenVPN Inc's own `openvpn3` core, AGPLv3, would force this whole
 * codebase open-source — OpenVPN Inc explicitly refuses commercial licensing on that code). IKEv2
 * needed no such tradeoff: `android.net.VpnManager`/`Ikev2VpnProfile` are PLATFORM APIs (Apache
 * 2.0, part of AOSP itself, API 30+) — zero third-party dependency, zero license exposure.
 *
 * Every method/constant referenced here was verified against the actual AOSP source
 * (frameworks/base `core/java/android/net/{Ikev2VpnProfile,VpnManager,VpnProfileState}.java`)
 * before being used — NOT assumed from general Android familiarity. Two hard platform
 * constraints fell out of that verification and are NOT workarounds pending a future batch —
 * they are ceilings of the platform API itself:
 *  - `Ikev2VpnProfile.Builder(...)` requires API 30 (`PackageManager.FEATURE_IPSEC_TUNNELS`,
 *    checked at runtime — most modern devices have it, but it's not universal).
 *  - `VpnManager.getProvisionedVpnProfileState()` and the `ACTION_VPN_MANAGER_EVENT` broadcast
 *    (the only public ways to observe connection state/errors) require API 33. On API 30-32
 *    there is NO public API for this at all (the equivalent fields exist in AOSP source but are
 *    `@hide` below API 33) — [state] on those OS versions is a best-effort optimistic guess, not
 *    a real signal, and is documented as such at the call site below.
 */
class IkeV2VpnEngine(context: Context) : VpnEngine {

    private val appContext = context.applicationContext
    override val mode: String = AppMode.IKEV2

    private val _state = MutableStateFlow<VpnEngineState>(VpnEngineState.Disconnected)
    override val state: StateFlow<VpnEngineState> = _state

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    private var eventReceiver: BroadcastReceiver? = null

    private val vpnManager: VpnManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appContext.getSystemService(VpnManager::class.java)
        } else {
            null
        }
    }

    /** True only when both the OS version AND the device's IPsec-tunnel feature support IKEv2. */
    private fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_IPSEC_TUNNELS)

    override suspend fun prepareConsent(config: VpnProtocolConfig): Intent? {
        require(config is VpnProtocolConfig.IkeV2) {
            "IkeV2VpnEngine only accepts VpnProtocolConfig.IkeV2, got ${config::class.simpleName}"
        }
        if (!isSupported()) return null // connect() surfaces the real reason as an Error state.
        val manager = vpnManager ?: return null
        return runCatching { manager.provisionVpnProfile(buildProfile(config)) }.getOrNull()
    }

    override suspend fun connect(config: VpnProtocolConfig) {
        require(config is VpnProtocolConfig.IkeV2) {
            "IkeV2VpnEngine only accepts VpnProtocolConfig.IkeV2, got ${config::class.simpleName}"
        }
        if (!isSupported()) {
            _state.value = VpnEngineState.Error(
                "IKEv2 native butuh Android 11 (API 30) ke atas dan dukungan IPsec tunnel " +
                    "perangkat ini — tidak tersedia di perangkat/OS saat ini."
            )
            return
        }
        val manager = vpnManager ?: run {
            _state.value = VpnEngineState.Error("VpnManager tidak tersedia di perangkat ini.")
            return
        }

        _state.value = VpnEngineState.Connecting
        try {
            val profile = buildProfile(config)
            val consentIntent = manager.provisionVpnProfile(profile)
            if (consentIntent != null) {
                // Caller skipped prepareConsent() or the user hasn't approved yet — surface this
                // as an explicit error rather than silently hanging in Connecting forever.
                _state.value = VpnEngineState.Error(
                    "Izin VPN IKEv2 belum diberikan — panggil prepareConsent() dan minta user " +
                        "menyetujui dialog sistem sebelum connect()."
                )
                return
            }
            manager.startProvisionedVpnProfileSession()
            startMonitoring(manager)
        } catch (e: Exception) {
            _state.value = VpnEngineState.Error("Gagal menyalakan IKEv2: ${e.message}")
        }
    }

    override suspend fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        unregisterEventReceiver()
        runCatching { vpnManager?.stopProvisionedVpnProfile() }
        _state.value = VpnEngineState.Disconnected
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildProfile(config: VpnProtocolConfig.IkeV2): Ikev2VpnProfile {
        val builder = Ikev2VpnProfile.Builder(config.serverAddress, config.identity)
        when {
            config.certificateAlias != null -> {
                val (cert, key) = loadFromAndroidKeystore(config.certificateAlias)
                // serverRootCa null == trust the system's public CA store, not a self-signed/
                // private CA. Acceptable default for this batch; a serverRootCa field can be
                // added to VpnProtocolConfig.IkeV2 later if a private CA is needed.
                builder.setAuthDigitalSignature(cert, key, null)
            }
            config.username != null && config.password != null -> {
                builder.setAuthUsernamePassword(config.username, config.password, null)
            }
            else -> throw IllegalArgumentException(
                "IkeV2 config butuh certificateAlias ATAU username+password terisi."
            )
        }
        // Full-tunnel by default, matching WARP's existing behavior (decision #6) — bypassable
        // (split-tunnel-by-app) isn't modeled by VpnProtocolConfig.IkeV2 at all (see its kdoc).
        builder.setBypassable(false)
        return builder.build()
    }

    /**
     * [alias] must already exist in AndroidKeyStore — this batch does NOT provision or import
     * certificates, it only consumes an alias the user/another flow already installed there.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun loadFromAndroidKeystore(alias: String): Pair<X509Certificate, PrivateKey> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val cert = keyStore.getCertificate(alias) as? X509Certificate
            ?: throw IllegalStateException("Sertifikat '$alias' tidak ditemukan di AndroidKeyStore.")
        val key = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw IllegalStateException("Private key '$alias' tidak ditemukan di AndroidKeyStore.")
        return cert to key
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startMonitoring(manager: VpnManager) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerEventReceiver()
            pollJob = engineScope.launch {
                while (isActive) {
                    pollState(manager)
                    delay(POLL_INTERVAL_MS)
                }
            }
        } else {
            // API 30-32: no public state/event API exists at all (see class kdoc). This is an
            // OPTIMISTIC GUESS, not a confirmed connection — startProvisionedVpnProfileSession()
            // not throwing only means the request was accepted, not that IKE negotiation
            // actually succeeded. Documented gap, not a bug to "fix" without a newer API level.
            _state.value = VpnEngineState.Connected(System.currentTimeMillis())
        }
    }

    @RequiresApi(33)
    private fun pollState(manager: VpnManager) {
        val profileState = runCatching { manager.provisionedVpnProfileState }.getOrNull() ?: return
        _state.value = when (profileState.state) {
            VpnProfileState.STATE_CONNECTED -> VpnEngineState.Connected(System.currentTimeMillis())
            VpnProfileState.STATE_CONNECTING -> VpnEngineState.Connecting
            VpnProfileState.STATE_FAILED -> VpnEngineState.Error("IKEv2 gagal terhubung.")
            else -> VpnEngineState.Disconnected
        }
    }

    @RequiresApi(33)
    private fun registerEventReceiver() {
        if (eventReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val category = intent.categories?.firstOrNull() ?: return
                when (category) {
                    VpnManager.CATEGORY_EVENT_IKE_ERROR,
                    VpnManager.CATEGORY_EVENT_NETWORK_ERROR -> {
                        _state.value = VpnEngineState.Error(
                            "IKEv2 error: kode ${intent.getIntExtra(VpnManager.EXTRA_ERROR_CODE, -1)}"
                        )
                    }
                    VpnManager.CATEGORY_EVENT_DEACTIVATED_BY_USER -> {
                        _state.value = VpnEngineState.Disconnected
                    }
                }
            }
        }
        val filter = IntentFilter(VpnManager.ACTION_VPN_MANAGER_EVENT).apply {
            addCategory(VpnManager.CATEGORY_EVENT_IKE_ERROR)
            addCategory(VpnManager.CATEGORY_EVENT_NETWORK_ERROR)
            addCategory(VpnManager.CATEGORY_EVENT_DEACTIVATED_BY_USER)
        }
        runCatching {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }.onSuccess { eventReceiver = receiver }
    }

    private fun unregisterEventReceiver() {
        eventReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        eventReceiver = null
    }

    companion object {
        private const val POLL_INTERVAL_MS = 10_000L
    }
}
