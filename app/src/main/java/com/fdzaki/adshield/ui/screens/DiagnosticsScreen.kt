package com.fdzaki.adshield.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldSurface
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.warp.WarpConnectionQuality
import com.wireguard.android.backend.Tunnel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only technical snapshot of the app's current state, plus a "copy all"
 * button so the user can paste the whole thing when reporting a bug (to
 * Claude/chat, GitHub issue, etc.) without having to describe each field by
 * hand. Every value here is read from state that already exists elsewhere
 * (MainViewModel, Build.*, PackageManager) — this screen doesn't introduce
 * any new source of truth, it just surfaces what's already there in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val activeMode by viewModel.activeMode.collectAsState()
    val vpnActive by viewModel.vpnActive.collectAsState()
    val dnsLastError by viewModel.dnsLastError.collectAsState()
    val warpState by viewModel.warpState.collectAsState()
    val warpConnecting by viewModel.warpConnecting.collectAsState()
    val warpLastError by viewModel.warpLastError.collectAsState()
    val warpQuality by viewModel.warpQuality.collectAsState()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val allowedCount by viewModel.allowedCount.collectAsState()
    val loggingEnabled by viewModel.loggingEnabled.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()

    val appVersion = remember { readAppVersion(context.packageManager, context.packageName) }

    val batteryExempt = remember {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    val modeLabel = when (activeMode) {
        AppMode.DNS_ADBLOCK -> "Ad-Block DNS"
        AppMode.WARP_TUNNEL -> "VPN Tunnel (WARP)"
        else -> "Tidak ada (nonaktif)"
    }

    val warpStateLabel = when {
        warpConnecting -> "Menyambungkan…"
        warpState == Tunnel.State.UP -> "Aktif"
        else -> "Nonaktif"
    }

    val qualityLabel = when (warpQuality.level) {
        WarpConnectionQuality.Level.UNKNOWN -> "Belum diperiksa"
        WarpConnectionQuality.Level.GOOD -> "Baik (${warpQuality.latencyMs} ms)"
        WarpConnectionQuality.Level.DEGRADED -> "Agak lambat (${warpQuality.latencyMs} ms)"
        WarpConnectionQuality.Level.BAD -> "Bermasalah"
    }

    val generatedAt = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    val diagnosticText = remember(
        activeMode, vpnActive, dnsLastError, warpState, warpConnecting,
        warpLastError, warpQuality, blockedCount, allowedCount, loggingEnabled,
        autoStartOnBoot, batteryExempt
    ) {
        buildString {
            appendLine("=== AdShield Diagnostik ===")
            appendLine("Dibuat: $generatedAt")
            appendLine()
            appendLine("Versi app: $appVersion")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Dikecualikan dari optimasi baterai: ${if (batteryExempt) "Ya" else "Tidak"}")
            appendLine()
            appendLine("Mode aktif: $modeLabel")
            appendLine("Auto-start setelah reboot: ${if (autoStartOnBoot) "Ya" else "Tidak"}")
            appendLine()
            appendLine("--- Ad-Block DNS ---")
            appendLine("Status: ${if (vpnActive) "Aktif" else "Nonaktif"}")
            appendLine("Log domain aktif: ${if (loggingEnabled) "Ya" else "Tidak"}")
            appendLine("Diblokir: $blockedCount, Diizinkan: $allowedCount")
            appendLine("Error terakhir: ${dnsLastError ?: "-"}")
            appendLine()
            appendLine("--- VPN Tunnel (WARP) ---")
            appendLine("Status: $warpStateLabel")
            appendLine("Kualitas koneksi: $qualityLabel")
            appendLine("Percobaan reconnect: ${warpQuality.reconnectAttempts}")
            appendLine("Error terakhir: ${warpLastError ?: "-"}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostik") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(diagnosticText))
                        Toast.makeText(context, "Info diagnostik disalin", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Salin info diagnostik")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Ringkasan status teknis AdShield saat ini. Salin lewat ikon di " +
                    "kanan atas kalau perlu melapor masalah.",
                fontSize = 12.sp,
                color = ShieldTextMuted
            )
            Spacer(Modifier.height(16.dp))

            DiagnosticSection(title = "Aplikasi & Perangkat") {
                DiagnosticRow("Versi app", appVersion)
                DiagnosticRow("Perangkat", "${Build.MANUFACTURER} ${Build.MODEL}")
                DiagnosticRow("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                DiagnosticRow(
                    "Optimasi baterai",
                    if (batteryExempt) "Dikecualikan (aman)" else "TIDAK dikecualikan",
                    valueColor = if (batteryExempt) ShieldGreen else ShieldDanger
                )
            }

            Spacer(Modifier.height(12.dp))

            DiagnosticSection(title = "Mode Aktif") {
                DiagnosticRow("Mode", modeLabel)
                DiagnosticRow("Auto-start setelah reboot", if (autoStartOnBoot) "Ya" else "Tidak")
            }

            Spacer(Modifier.height(12.dp))

            DiagnosticSection(title = "Ad-Block DNS") {
                DiagnosticRow(
                    "Status",
                    if (vpnActive) "Aktif" else "Nonaktif",
                    valueColor = if (vpnActive) ShieldGreen else ShieldTextMuted
                )
                DiagnosticRow("Diblokir / Diizinkan", "$blockedCount / $allowedCount")
                DiagnosticRow("Log domain aktif", if (loggingEnabled) "Ya" else "Tidak")
                if (dnsLastError != null) {
                    DiagnosticRow("Error terakhir", dnsLastError ?: "-", valueColor = ShieldDanger)
                }
            }

            Spacer(Modifier.height(12.dp))

            DiagnosticSection(title = "VPN Tunnel (WARP)") {
                DiagnosticRow(
                    "Status",
                    warpStateLabel,
                    valueColor = if (warpState == Tunnel.State.UP) ShieldGreen else ShieldTextMuted
                )
                DiagnosticRow("Kualitas koneksi", qualityLabel)
                DiagnosticRow("Percobaan reconnect", warpQuality.reconnectAttempts.toString())
                if (warpLastError != null) {
                    DiagnosticRow("Error terakhir", warpLastError ?: "-", valueColor = ShieldDanger)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Suppress("DEPRECATION") // getPackageInfo(String, Int) + versionCode: simplest form that still works on minSdk 24
private fun readAppVersion(packageManager: PackageManager, packageName: String): String {
    return try {
        val info = packageManager.getPackageInfo(packageName, 0)
        "${info.versionName} (build ${info.versionCode})"
    } catch (_: PackageManager.NameNotFoundException) {
        "Tidak diketahui"
    }
}

@Composable
private fun DiagnosticSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = ShieldTextMuted) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = ShieldTextMuted)
        Text(value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}
