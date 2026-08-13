package com.fdzaki.adshield

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fdzaki.adshield.ui.UiEvent
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.screens.DiagnosticsScreen
import com.fdzaki.adshield.ui.screens.HomeScreen
import com.fdzaki.adshield.ui.screens.LogsScreen
import com.fdzaki.adshield.ui.screens.OnboardingScreen
import com.fdzaki.adshield.ui.screens.RulesScreen
import com.fdzaki.adshield.ui.screens.SilentLeakScreen
import com.fdzaki.adshield.ui.screens.WhitelistScreen
import com.fdzaki.adshield.ui.theme.AdShieldTheme
import com.fdzaki.adshield.ui.theme.ShieldBgDark
import com.fdzaki.adshield.qs.DnsTileService
import com.fdzaki.adshield.qs.WarpTileService
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.util.ShortcutsManager
import com.fdzaki.adshield.vpn.AdBlockVpnService
import com.fdzaki.adshield.warp.WarpForegroundService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Which mode we were trying to start when the VPN permission dialog was
     *  launched — the system callback tells us permission was granted, but
     *  not what for, so we track it ourselves. */
    private var pendingStartMode: String? = null

    /** Set true only when this Activity instance was launched by a QS tile
     *  purely to show the one-time VpnService consent dialog (permission
     *  never granted / revoked) — see DnsTileService/WarpTileService
     *  ACTION_REQUEST_PERMISSION and handleTilePermissionIntent() below.
     *  When true, vpnPermissionLauncher's callback finishes this Activity
     *  right after handling the result instead of composing the normal UI,
     *  so the tile never actually "opens the app" beyond the unavoidable
     *  system permission dialog itself. */
    private var finishAfterPendingStart: Boolean = false

    /** Set from a static shortcut's EXTRA_SHORTCUT_DEST (see
     *  res/xml/shortcuts.xml) so the NavHost can jump straight to that
     *  screen once it's composed, instead of always landing on Home first. */
    private var pendingNavDestination by mutableStateOf<String?>(null)

    /** Null while we haven't yet read the persisted onboarding flag (one-shot
     *  suspend read, see MainViewModel.currentHasSeenOnboarding — same reason
     *  as pendingStartMode/currentActiveMode: a StateFlow's stateIn() seed
     *  value can't be trusted before something has subscribed to it). Once
     *  non-null, NavHost's start destination is decided and won't change. */
    private var startAtOnboarding by mutableStateOf<Boolean?>(null)

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            when (pendingStartMode) {
                AppMode.DNS_ADBLOCK -> startDnsService()
                AppMode.WARP_TUNNEL -> startWarpService()
            }
        } else if (finishAfterPendingStart) {
            // Tile-launched consent-only flow has no composed UI/Snackbar host
            // to surface viewModel.notifyVpnPermissionDenied() into — Toast is
            // the only feedback channel available here.
            Toast.makeText(this, TILE_PERMISSION_DENIED_MESSAGE, Toast.LENGTH_SHORT).show()
        } else {
            // Feedback audit finding: this branch was previously empty — user
            // taps the ring, denies the system VPN dialog, and nothing at all
            // happens. Now surfaces via the global Snackbar (see setContent).
            viewModel.notifyVpnPermissionDenied()
        }
        pendingStartMode = null
        if (finishAfterPendingStart) {
            finishAfterPendingStart = false
            finish()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: notification just won't show if denied, protection still runs */ }

    /** ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS's own resultCode is not a
     *  reliable signal of grant/deny on many OEM ROMs, so the callback here
     *  ignores `result` entirely and instead re-reads the ground truth
     *  (PowerManager.isIgnoringBatteryOptimizations) once the system dialog
     *  returns control to the app — see notifyBatteryExemptionResult(). */
    private val batteryExemptionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        viewModel.notifyBatteryExemptionResult(pm.isIgnoringBatteryOptimizations(packageName))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() to take effect for this launch.
        // Transparent theme so the tile's consent-only detour never visibly
        // "opens the app" — just the unavoidable system VPN dialog on top.
        if (isTilePermissionAction(intent?.action)) {
            setTheme(R.style.Theme_AdShield_Transparent)
        }
        super.onCreate(savedInstanceState)

        if (handleTilePermissionIntent(intent)) return

        pendingNavDestination = intent?.getStringExtra(ShortcutsManager.EXTRA_SHORTCUT_DEST)
        handleShortcutToggleIntent(intent)

        setContent {
            // v4.7.0 — custom theme #2 toggle: was `AdShieldTheme { ... }` with
            // no argument (always TITANIUM_BRASS default). See PROJECT_STATE.md
            // / ThemeVariant.kt.
            val themeVariant by viewModel.themeVariant.collectAsState()
            AdShieldTheme(themeVariant = themeVariant) {
                val navController = rememberNavController()

                // Single Snackbar host shared by every screen in the NavHost —
                // added in the feedback audit pass so any screen can surface a
                // confirmation via viewModel.uiEvents without each screen having
                // to declare its own Scaffold/SnackbarHostState (see PROJECT_STATE.md).
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.Message -> {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(
                                    message = event.text,
                                    duration = SnackbarDuration.Short
                                )
                            }
                            is UiEvent.UndoableMessage -> {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = event.text,
                                    actionLabel = "Urungkan",
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) event.onUndo()
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    maybeRequestNotificationPermission()
                }

                LaunchedEffect(Unit) {
                    startAtOnboarding = !viewModel.currentHasSeenOnboarding()
                }

                LaunchedEffect(pendingNavDestination) {
                    val dest = pendingNavDestination
                    // v4.7.7: tambah "diagnostics" — dipakai aksi notif WARP baru
                    // (WarpForegroundService.buildNotification()'s tombol "Diagnostik").
                    // Pola & mekanisme SAMA PERSIS dengan "whitelist"/"logs" yang sudah
                    // ada (shortcut statis) — cuma nambah 1 string ke whitelist ini.
                    if (dest == "whitelist" || dest == "logs" || dest == "diagnostics") {
                        navController.navigate(dest)
                    }
                    pendingNavDestination = null
                }

                val onboardingDecided = startAtOnboarding
                if (onboardingDecided == null) {
                    // Brief gate so home never flashes before we know whether
                    // this is a first run — same dark background as the rest
                    // of the app so it reads as a fast load, not a blank screen.
                    Box(modifier = Modifier.fillMaxSize().background(ShieldBgDark))
                    return@AdShieldTheme
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = ShieldBgDark
                ) { scaffoldPadding ->
                NavHost(
                    modifier = Modifier.padding(scaffoldPadding),
                    navController = navController,
                    startDestination = if (onboardingDecided) "onboarding" else "home"
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinish = {
                                viewModel.markOnboardingComplete()
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            },
                            onRequestBatteryExemption = ::requestBatteryOptimizationExemption
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onRequestVpnStart = ::requestVpnPermissionThenStartDns,
                            onStopVpn = { stopDnsService() },
                            onRequestWarpStart = ::requestVpnPermissionThenStartWarp,
                            onStopWarp = { stopWarpService() },
                            onOpenWhitelist = { navController.navigate("whitelist") },
                            onOpenRules = { navController.navigate("rules") },
                            onOpenLogs = { navController.navigate("logs") },
                            onOpenDiagnostics = { navController.navigate("diagnostics") },
                            onOpenSilentLeaks = { navController.navigate("silent_leaks") },
                            onRequestBatteryExemption = ::requestBatteryOptimizationExemption
                        )
                    }
                    composable("silent_leaks") {
                        SilentLeakScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("whitelist") {
                        WhitelistScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("rules") {
                        RulesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("logs") {
                        LogsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("diagnostics") {
                        DiagnosticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleTilePermissionIntent(intent)) return
        pendingNavDestination = intent.getStringExtra(ShortcutsManager.EXTRA_SHORTCUT_DEST)
        handleShortcutToggleIntent(intent)
    }

    private fun isTilePermissionAction(action: String?): Boolean =
        action == DnsTileService.ACTION_REQUEST_PERMISSION || action == WarpTileService.ACTION_REQUEST_PERMISSION

    /** Handles the QS tiles' consent-only launch (see DnsTileService /
     *  WarpTileService — this Activity is opened ONLY because Android
     *  requires a foreground Activity for the VpnService permission
     *  dialog, never for manual activation). Returns true if the intent
     *  was one of these and was fully handled — caller must skip
     *  composing the normal UI in that case. */
    private fun handleTilePermissionIntent(intent: Intent?): Boolean {
        val mode = when (intent?.action) {
            DnsTileService.ACTION_REQUEST_PERMISSION -> AppMode.DNS_ADBLOCK
            WarpTileService.ACTION_REQUEST_PERMISSION -> AppMode.WARP_TUNNEL
            else -> return false
        }
        finishAfterPendingStart = true
        pendingStartMode = mode
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            // Defensive fallback only — the tiles already check this
            // themselves and wouldn't normally launch this Activity at all
            // when permission is already granted.
            when (mode) {
                AppMode.DNS_ADBLOCK -> startDnsService()
                AppMode.WARP_TUNNEL -> startWarpService()
            }
            finishAfterPendingStart = false
            pendingStartMode = null
            finish()
        }
        return true
    }

    /** Handles the two dynamic toggle shortcuts (see ShortcutsManager /
     *  AdShieldApp). Reads the true persisted mode via a one-shot suspend
     *  call — NOT viewModel.activeMode.value, which on a cold start would
     *  still just be its stateIn() seed value (AppMode.NONE) since nothing
     *  has subscribed to it yet, and could toggle the wrong direction. */
    private fun handleShortcutToggleIntent(intent: Intent?) {
        val action = intent?.action
        if (action != ShortcutsManager.ACTION_TOGGLE_DNS && action != ShortcutsManager.ACTION_TOGGLE_WARP) return

        lifecycleScope.launch {
            val mode = viewModel.currentActiveMode()
            when (action) {
                ShortcutsManager.ACTION_TOGGLE_DNS -> {
                    if (mode == AppMode.DNS_ADBLOCK) stopDnsService() else requestVpnPermissionThenStartDns()
                }
                ShortcutsManager.ACTION_TOGGLE_WARP -> {
                    if (mode == AppMode.WARP_TUNNEL) stopWarpService() else requestVpnPermissionThenStartWarp()
                }
            }
        }
    }

    private fun requestVpnPermissionThenStartDns() {
        pendingStartMode = AppMode.DNS_ADBLOCK
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent) else startDnsService()
    }

    private fun requestVpnPermissionThenStartWarp() {
        pendingStartMode = AppMode.WARP_TUNNEL
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent) else startWarpService()
    }

    /** Modes are mutually exclusive (see PROJECT_STATE.md) — starting one
     *  always sends a stop to the other first. Stopping an already-stopped
     *  service is a harmless no-op. The stop sent here is flagged as a mode
     *  switch (EXTRA_MODE_SWITCH) so the stopping service does NOT also
     *  write AppMode.NONE — that write would race against the newly
     *  starting service's own AppMode write, since they run on independent
     *  coroutine scopes with no ordering guarantee (see AdBlockVpnService /
     *  WarpForegroundService for details). */
    // Feedback audit finding (v3.8.1): these used to call viewModel.setVpnActive(true/false)
    // right here — the instant the tap happened, regardless of whether
    // AdBlockVpnService.startVpn() actually managed to establish the VPN interface.
    // viewModel.vpnActive is now derived straight from the persisted activeMode
    // (see MainViewModel), which AdBlockVpnService only writes DNS_ADBLOCK into AFTER a
    // successful establish() — so no separate manual flip is needed or correct here
    // anymore; the ring updates itself once the real state lands.
    private fun startDnsService() {
        stopWarpService(isModeSwitch = true)
        val intent = Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopDnsService(isModeSwitch: Boolean = false) {
        val intent = Intent(this, AdBlockVpnService::class.java)
            .setAction(AdBlockVpnService.ACTION_STOP)
            .putExtra(AdBlockVpnService.EXTRA_MODE_SWITCH, isModeSwitch)
        startService(intent)
    }

    private fun startWarpService() {
        stopDnsService(isModeSwitch = true)
        val intent = Intent(this, WarpForegroundService::class.java).setAction(WarpForegroundService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopWarpService(isModeSwitch: Boolean = false) {
        val intent = Intent(this, WarpForegroundService::class.java)
            .setAction(WarpForegroundService.ACTION_STOP)
            .putExtra(WarpForegroundService.EXTRA_MODE_SWITCH, isModeSwitch)
        startService(intent)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Whitelisting from Doze/App-Standby is what actually keeps a background
     *  VPN service alive long-term on aggressive OEM skins (Infinix XOS,
     *  MIUI, ColorOS, etc.) — this is separate from Recents-swipe survival. */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            // Feedback audit finding (round 2): previously a bare runCatching
            // with no fallback — if the Intent itself failed to launch (some
            // OEM ROMs block it outright, e.g. Infinix XOS), the user tapped
            // the button and nothing happened, with zero explanation.
            runCatching { batteryExemptionLauncher.launch(intent) }
                .onFailure { viewModel.notifyBatteryExemptionUnavailable() }
        } else {
            viewModel.notifyBatteryExemptionResult(granted = true)
        }
    }

    private companion object {
        // Mirrors MainViewModel.notifyVpnPermissionDenied()'s Snackbar text —
        // duplicated here because the tile consent-only flow never composes
        // a UI, so the Snackbar host isn't available to send that event into.
        const val TILE_PERMISSION_DENIED_MESSAGE =
            "Izin VPN ditolak — AdShield butuh izin ini untuk memfilter DNS/trafik"
    }
}
