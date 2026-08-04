package com.fdzaki.adshield

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
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
import com.fdzaki.adshield.ui.screens.WhitelistScreen
import com.fdzaki.adshield.ui.theme.AdShieldTheme
import com.fdzaki.adshield.ui.theme.ShieldBgDark
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
        } else {
            // Feedback audit finding: this branch was previously empty — user
            // taps the ring, denies the system VPN dialog, and nothing at all
            // happens. Now surfaces via the global Snackbar (see setContent).
            viewModel.notifyVpnPermissionDenied()
        }
        pendingStartMode = null
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
        super.onCreate(savedInstanceState)

        pendingNavDestination = intent?.getStringExtra(ShortcutsManager.EXTRA_SHORTCUT_DEST)
        handleShortcutToggleIntent(intent)

        setContent {
            AdShieldTheme {
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
                    if (dest == "whitelist" || dest == "logs") {
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
                            onRequestBatteryExemption = ::requestBatteryOptimizationExemption
                        )
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
        pendingNavDestination = intent.getStringExtra(ShortcutsManager.EXTRA_SHORTCUT_DEST)
        handleShortcutToggleIntent(intent)
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
    private fun startDnsService() {
        stopWarpService(isModeSwitch = true)
        val intent = Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        viewModel.setVpnActive(true)
    }

    private fun stopDnsService(isModeSwitch: Boolean = false) {
        val intent = Intent(this, AdBlockVpnService::class.java)
            .setAction(AdBlockVpnService.ACTION_STOP)
            .putExtra(AdBlockVpnService.EXTRA_MODE_SWITCH, isModeSwitch)
        startService(intent)
        viewModel.setVpnActive(false)
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
}
