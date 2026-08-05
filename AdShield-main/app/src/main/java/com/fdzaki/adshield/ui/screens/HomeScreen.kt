package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.theme.ShieldAccentDim
import com.fdzaki.adshield.ui.theme.ShieldBgDark
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldGreen
import com.fdzaki.adshield.ui.theme.ShieldMonoStat
import com.fdzaki.adshield.ui.theme.ShieldOutline
import com.fdzaki.adshield.ui.theme.ShieldSurface
import com.fdzaki.adshield.ui.theme.ShieldSurface2
import com.fdzaki.adshield.ui.theme.ShieldTextFaint
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.ui.theme.ShieldWarning
import com.fdzaki.adshield.warp.WarpConnectionQuality
import com.wireguard.android.backend.Tunnel

/**
 * v3.0.0 visual identity — "Matte Graphite / Jade Signal". Same
 * ViewModel/state wiring as before; only the presentation layer changed.
 * Signature element: the status ring (`ProtectionRing`) — a matte disc with
 * a thin instrument-style ring instead of a flat-filled button, echoing how
 * premium VPN clients (Mullvad, WARP) present connection state as something
 * closer to hardware than a checkbox. Definition throughout comes from 1dp
 * hairline borders (`ShieldOutline`), not drop shadows — that's the "matte"
 * half of the brief.
 */
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
    val dnsError by viewModel.dnsLastError.collectAsState()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val allowedCount by viewModel.allowedCount.collectAsState()
    val warpState by viewModel.warpState.collectAsState()
    val warpConnecting by viewModel.warpConnecting.collectAsState()
    val warpError by viewModel.warpLastError.collectAsState()
    val warpQuality by viewModel.warpQuality.collectAsState()
    val warpRouteIpv6 by viewModel.warpRouteIpv6.collectAsState()
    val warpUp = warpState == Tunnel.State.UP

    // Feedback audit finding: "Reset statistik" used to fire on a single tap
    // with no confirmation and no undo. Now gated behind a confirm dialog —
    // an AlertDialog was chosen over Snackbar+Undo because the counters
    // aren't cheap to restore (would need to re-derive from log history).
    var showResetConfirm by remember { mutableStateOf(false) }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset statistik?") },
            text = { Text("Jumlah domain diblokir dan diizinkan akan dikembalikan ke nol. Log domain tidak terpengaruh.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetCounters()
                    showResetConfirm = false
                }) { Text("Reset", color = ShieldDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Batal") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShieldBgDark)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        BrandHeader()

        Spacer(Modifier.height(36.dp))

        ProtectionRing(active = vpnActive) {
            if (vpnActive) onStopVpn() else onRequestVpnStart()
        }

        Spacer(Modifier.height(20.dp))
        Text(
            if (vpnActive) "PERLINDUNGAN AKTIF" else "PERLINDUNGAN NONAKTIF",
            style = MaterialTheme.typography.labelLarge,
            color = if (vpnActive) ShieldGreen else ShieldTextMuted
        )

        // Feedback audit finding (v3.8.1): dnsLastError was only ever surfaced on the
        // Diagnostics screen — a DNS establish() failure left the ring silently showing
        // "NONAKTIF" with no explanation on the screen the user actually lands on, unlike
        // WARP's card below which already shows `error = warpError` inline. Only shown
        // while not active, so it clears itself the moment a start actually succeeds.
        if (!vpnActive && dnsError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                dnsError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = ShieldDanger,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "DIBLOKIR",
                value = blockedCount.toString(),
                color = ShieldDanger
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "DIIZINKAN",
                value = allowedCount.toString(),
                color = ShieldGreen
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, ShieldOutline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ShieldTextMuted)
        ) { Text("Reset statistik", style = MaterialTheme.typography.labelLarge) }

        Spacer(Modifier.height(28.dp))

        WarpModeCard(
            active = warpUp,
            connecting = warpConnecting,
            error = warpError,
            quality = warpQuality,
            routeIpv6 = warpRouteIpv6,
            onToggleRouteIpv6 = { viewModel.setWarpRouteIpv6(it) },
            onToggle = { turnOn ->
                if (turnOn) onRequestWarpStart() else onStopWarp()
            }
        )
        Text(
            "Registrasi otomatis ke API gratis Cloudflare WARP yang tidak resmi " +
                "(dipakai proyek open-source seperti wgcf) — bisa berhenti berfungsi " +
                "kalau Cloudflare mengubah API tanpa pemberitahuan.",
            color = ShieldTextFaint,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(12.dp))

        NavGroup {
            NavRow(icon = Icons.Filled.List, label = "Daftar Log Domain", onClick = onOpenLogs)
            NavDivider()
            NavRow(icon = Icons.Filled.Rule, label = "Whitelist per Aplikasi", onClick = onOpenWhitelist)
            NavDivider()
            NavRow(icon = Icons.Filled.Shield, label = "Aturan Kustom (Block/Allow)", onClick = onOpenRules)
            NavDivider()
            NavRow(icon = Icons.Filled.BugReport, label = "Diagnostik", onClick = onOpenDiagnostics)
        }

        Spacer(Modifier.height(20.dp))

        TextButton(onClick = onRequestBatteryExemption) {
            Text(
                "Kecualikan dari optimasi baterai (disarankan)",
                fontSize = 12.sp,
                color = ShieldGreen
            )
        }
        Text(
            "Agar servis tidak dimatikan sistem XOS/MIUI/ColorOS saat idle, izinkan " +
                "\"No restrictions\" di pengaturan baterai perangkat untuk AdShield.",
            color = ShieldTextFaint,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BrandHeader() {
    Text(
        "ADSHIELD",
        style = MaterialTheme.typography.labelLarge,
        color = ShieldTextMuted
    )
    Spacer(Modifier.height(2.dp))
    Text(
        "Pemblokir iklan & pelacak on-device",
        style = MaterialTheme.typography.bodySmall,
        color = ShieldTextFaint
    )
}

/**
 * Signature element. A matte disc (elevation via tone, not shadow) framed by
 * a thin instrument ring: solid + bright when protection is on, a faint
 * hairline track when off. No fill animation on toggle — deliberately
 * restrained, a single calm state change rather than a flashy transition.
 */
@Composable
private fun ProtectionRing(active: Boolean, onClick: () -> Unit) {
    val ringColor = ShieldGreen
    val trackColor = ShieldOutline

    Box(
        modifier = Modifier.size(184.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            drawCircle(
                color = trackColor,
                radius = (size.minDimension - strokeWidth) / 2f,
                style = Stroke(width = strokeWidth)
            )
            if (active) {
                drawCircle(
                    color = ringColor,
                    radius = (size.minDimension - strokeWidth) / 2f,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(CircleShape)
                .background(if (active) ShieldAccentDim else ShieldSurface2)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Shield else Icons.Filled.PowerSettingsNew,
                contentDescription = if (active) "Matikan proteksi" else "Aktifkan proteksi",
                tint = if (active) ShieldGreen else ShieldTextMuted,
                modifier = Modifier.size(56.dp)
            )
        }
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
    routeIpv6: Boolean,
    onToggleRouteIpv6: (Boolean) -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface),
        border = BorderStroke(1.dp, if (active) ShieldGreen.copy(alpha = 0.4f) else ShieldOutline)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ShieldAccentDim else ShieldSurface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (active) ShieldGreen else ShieldTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("VPN Tunnel (WARP)", style = MaterialTheme.typography.titleMedium)
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
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = ShieldGreen,
                        checkedThumbColor = ShieldSurface
                    )
                )
            }
            if (active) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = ShieldOutline, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))
                WarpQualityRow(quality)
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text("Gagal: $error", color = ShieldDanger, fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ShieldOutline, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Rutekan IPv6 lewat WARP",
                        fontSize = 12.sp,
                        color = ShieldTextMuted
                    )
                    Text(
                        "Default nonaktif — di banyak jaringan seluler, jalur IPv6 WARP " +
                            "justru bikin upload jauh lebih lambat. Berlaku saat WARP " +
                            "dinyalakan ulang.",
                        fontSize = 10.sp,
                        color = ShieldTextFaint
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = routeIpv6,
                    onCheckedChange = onToggleRouteIpv6,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = ShieldGreen,
                        checkedThumbColor = ShieldSurface
                    )
                )
            }
        }
    }
}

/**
 * Small status row shown only while the WARP tunnel is active: a colored dot
 * for at-a-glance health, plus a monospace latency/reconnect readout —
 * numbers as instrument data. Reflects [WarpTunnelManager]'s periodic
 * trace-probe watchdog — not just "interface is up," but "traffic is
 * confirmed reaching Cloudflare via WARP."
 */
@Composable
private fun WarpQualityRow(quality: WarpConnectionQuality) {
    // v3.7.1: surface packet-loss (v3.7.0 field, previously computed but never
    // shown anywhere) as a compact suffix — only when it's actually non-zero,
    // so the common case (0% loss) doesn't clutter this glance-level row.
    val lossSuffix = if (quality.packetLossPercent > 0) " · loss ${quality.packetLossPercent}%" else ""
    val (dotColor, label) = when (quality.level) {
        WarpConnectionQuality.Level.UNKNOWN -> ShieldTextMuted to "Memeriksa kualitas jalur…"
        WarpConnectionQuality.Level.GOOD -> ShieldGreen to "${quality.latencyMs} ms · jalur baik$lossSuffix"
        WarpConnectionQuality.Level.DEGRADED -> ShieldWarning to "${quality.latencyMs} ms · agak lambat$lossSuffix"
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
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = ShieldMonoStat.copy(fontSize = 11.sp), color = ShieldTextMuted)
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ShieldSurface),
        border = BorderStroke(1.dp, ShieldOutline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = ShieldMonoStat.copy(fontSize = 26.sp), color = color)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = ShieldTextMuted)
        }
    }
}

/** Groups nav rows into a single hairline-bordered card, "premium settings list" style. */
@Composable
private fun NavGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface),
        border = BorderStroke(1.dp, ShieldOutline)
    ) {
        Column(content = content)
    }
}

@Composable
private fun NavDivider() {
    HorizontalDivider(
        color = ShieldOutline,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 60.dp)
    )
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ShieldSurface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ShieldGreen, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = ShieldTextFaint,
            modifier = Modifier.size(18.dp)
        )
    }
}
