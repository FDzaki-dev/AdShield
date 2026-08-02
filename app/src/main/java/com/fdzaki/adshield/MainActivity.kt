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
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.screens.HomeScreen
import com.fdzaki.adshield.ui.screens.LogsScreen
import com.fdzaki.adshield.ui.screens.RulesScreen
import com.fdzaki.adshield.ui.screens.WhitelistScreen
import com.fdzaki.adshield.ui.theme.AdShieldTheme
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.vpn.AdBlockVpnService
import com.fdzaki.adshield.warp.WarpForegroundService

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Which mode we were trying to start when the VPN permission dialog was
     *  launched — the system callback tells us permission was granted, but
     *  not what for, so we track it ourselves. */
    private var pendingStartMode: String? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            when (pendingStartMode) {
                AppMode.DNS_ADBLOCK -> startDnsService()
                AppMode.WARP_TUNNEL -> startWarpService()
            }
        }
        pendingStartMode = null
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: notification just won't show if denied, protection still runs */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AdShieldTheme {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    maybeRequestNotificationPermission()
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onRequestVpnStart = ::requestVpnPermissionThenStartDns,
                            onStopVpn = ::stopDnsService,
                            onRequestWarpStart = ::requestVpnPermissionThenStartWarp,
                            onStopWarp = ::stopWarpService,
                            onOpenWhitelist = { navController.navigate("whitelist") },
                            onOpenRules = { navController.navigate("rules") },
                            onOpenLogs = { navController.navigate("logs") },
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
     *  service is a harmless no-op. */
    private fun startDnsService() {
        stopWarpService()
        val intent = Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        viewModel.setVpnActive(true)
    }

    private fun stopDnsService() {
        val intent = Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP)
        startService(intent)
        viewModel.setVpnActive(false)
    }

    private fun startWarpService() {
        stopDnsService()
        val intent = Intent(this, WarpForegroundService::class.java).setAction(WarpForegroundService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopWarpService() {
        val intent = Intent(this, WarpForegroundService::class.java).setAction(WarpForegroundService.ACTION_STOP)
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
            runCatching { startActivity(intent) }
        }
    }
}
