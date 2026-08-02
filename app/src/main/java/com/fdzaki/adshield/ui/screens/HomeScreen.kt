package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldSurface
import com.fdzaki.adshield.ui.theme.ShieldSurfaceAlt
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.ui.theme.ShieldWarning
import com.fdzaki.adshield.warp.WarpConnectionQuality
import com.wireguard.android.backend.Tunnel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestVpnStart: () -> Unit,
    onStopVpn: () -> Unit,
    onRequestWarpStart: () -> Unit,
    onStopWarp: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    val vpnActive by viewModel.vpnActive.collectAsState()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val allowedCount by viewModel.allowedCount.collectAsState()
    val warpState by viewModel.warpState.collectAsState()
    val warpConnecting by viewModel.warpConnecting.collectAsState()
    val warpError by viewModel.warpLastError.collectAsState()
    val warpQuality by viewModel.warpQuality.collectAsState()
    val warpUp = warpState == Tunnel.State.UP

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("AdShield", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Pemblokir iklan & pelacak on-device",
            color = ShieldTextMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(40.dp))

        ShieldToggleButton(active = vpnActive) {
            if (vpnActive) onStopVpn() else onRequestVpnStart()
        }

        Spacer(Modifier.height(16.dp))
        Text(
            if (vpnActive) "Perlindungan aktif" else "Perlindungan nonaktif",
            color = if (vpnActive) ShieldGreen else ShieldTextMuted,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(32.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Diblokir",
                value = blockedCount.toString(),
                color = ShieldDanger
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Diizinkan",
                value = allowedCount.toString(),
                color = ShieldGreen
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { viewModel.resetCounters() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reset statistik") }

        Spacer(Modifier.height(24.dp))

        WarpModeCard(
            active = warpUp,
            connecting = warpConnecting,
            error = warpError,
            quality = warpQuality,
            onToggle = { turnOn ->
                if (turnOn) onRequestWarpStart() else onStopWarp()
            }
        )
        Text(
            "Registrasi otomatis ke API gratis Cloudflare WARP yang tidak resmi " +
                "(dipakai proyek open-source seperti wgcf) — bisa berhenti berfungsi " +
                "kalau Cloudflare mengubah API tanpa pemberitahuan.",
            color = ShieldTextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(28.dp))

        NavRow(icon = Icons.Filled.List, label = "Daftar Log Domain", onClick = onOpenLogs)
        Spacer(Modifier.height(10.dp))
        NavRow(icon = Icons.Filled.Rule, label = "Whitelist per Aplikasi", onClick = onOpenWhitelist)
        Spacer(Modifier.height(10.dp))
        NavRow(icon = Icons.Filled.Shield, label = "Aturan Kustom (Block/Allow)", onClick = onOpenRules)
        Spacer(Modifier.height(10.dp))
        NavRow(icon = Icons.Filled.BugReport, label = "Diagnostik", onClick = onOpenDiagnostics)

        Spacer(Modifier.height(20.dp))

        TextButton(onClick = onRequestBatteryExemption) {
            Text("Kecualikan dari optimasi baterai (disarankan)", fontSize = 12.sp)
        }
        Text(
            "Agar servis tidak dimatikan sistem XOS/MIUI/ColorOS saat idle, izinkan " +
                "\"No restrictions\" di pengaturan baterai perangkat untuk AdShield.",
            color = ShieldTextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * Full-tunnel encrypted VPN mode via Cloudflare WARP (WireGuard protocol).
 * Mutually exclusive with the DNS ad-block toggle above — turning this on
 * automatically turns DNS mode off (and vice versa), enforced in
 * MainActivity, not here; this card only reflects/requests state.
 */
@Composable
private fun WarpModeCard(
    active: Boolean,
    connecting: Boolean,
    error: String?,
    quality: WarpConnectionQuality,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = if (active) ShieldGreen else ShieldTextMuted)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("VPN Tunnel (WARP)", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            connecting -> "Menyambungkan…"
                            active -> "Semua trafik terenkripsi lewat Cloudflare"
                            else -> "Full-tunnel WireGuard, mode terpisah dari Ad-Block DNS"
                        },
                        fontSize = 12.sp,
                        color = if (active) ShieldGreen else ShieldTextMuted
                    )
                }
                Switch(
                    checked = active,
                    enabled = !connecting,
                    onCheckedChange = onToggle
                )
            }
            if (active) {
                Spacer(Modifier.height(10.dp))
                WarpQualityRow(quality)
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Gagal: $error",
                    color = ShieldDanger,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Small status row shown only while the WARP tunnel is active: a colored dot for
 * at-a-glance health, plus latency/reconnect text. Reflects [WarpTunnelManager]'s
 * periodic trace-probe watchdog — not just "interface is up", but "traffic is
 * confirmed reaching Cloudflare via WARP".
 */
@Composable
private fun WarpQualityRow(quality: WarpConnectionQuality) {
    val (dotColor, label) = when (quality.level) {
        WarpConnectionQuality.Level.UNKNOWN -> ShieldTextMuted to "Memeriksa kualitas jalur…"
        WarpConnectionQuality.Level.GOOD -> ShieldGreen to "Latensi ${quality.latencyMs} ms • jalur baik"
        WarpConnectionQuality.Level.DEGRADED -> ShieldWarning to "Latensi ${quality.latencyMs} ms • agak lambat"
        WarpConnectionQuality.Level.BAD ->
            if (quality.reconnectAttempts > 0) {
                ShieldDanger to "Menyambung ulang… (percobaan ke-${quality.reconnectAttempts})"
            } else {
                ShieldDanger to "Trafik belum terkonfirmasi lewat WARP"
            }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 11.sp, color = ShieldTextMuted)
    }
}

@Composable
private fun ShieldToggleButton(active: Boolean, onClick: () -> Unit) {
    val bg = if (active) ShieldGreen else ShieldSurfaceAlt
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(160.dp)) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = if (active) "Matikan proteksi" else "Aktifkan proteksi",
                tint = if (active) Color.Black else ShieldTextMuted,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 12.sp, color = ShieldTextMuted)
        }
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface),
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ShieldGreen)
            Spacer(Modifier.width(12.dp))
            Text(label)
        }
    }
}
