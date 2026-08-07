package com.fdzaki.adshield.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import com.fdzaki.adshield.data.VpnProfileRepository
import com.fdzaki.adshield.protocol.VpnEngineState
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
    onRequestIkeV2Start: () -> Unit,
    onStopIkeV2: () -> Unit,
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
    val ikeV2State by viewModel.ikeV2State.collectAsState()
    val ikeV2Profile by viewModel.ikeV2Profile.collectAsState()
    val ikeV2Up = ikeV2State is VpnEngineState.Connected
    val ikeV2Connecting = ikeV2State is VpnEngineState.Connecting
    val ikeV2Error = (ikeV2State as? VpnEngineState.Error)?.message

    var showIkeV2Editor by remember { mutableStateOf(false) }
    if (showIkeV2Editor) {
        IkeV2ProfileDialog(
            initial = ikeV2Profile,
            onDismiss = { showIkeV2Editor = false },
            onSave = { server, identity, username, password ->
                viewModel.saveIkeV2Profile(server, identity, username, password)
                showIkeV2Editor = false
            }
        )
    }

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
                value = formatStatCount(blockedCount),
                color = ShieldDanger
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "DIIZINKAN",
                value = formatStatCount(allowedCount),
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

        IkeV2ModeCard(
            active = ikeV2Up,
            connecting = ikeV2Connecting,
            error = ikeV2Error,
            hasProfile = ikeV2Profile != null,
            onToggle = { turnOn ->
                if (turnOn) onRequestIkeV2Start() else onStopIkeV2()
            },
            onEditProfile = { showIkeV2Editor = true }
        )
        Text(
            "IKEv2 native (android.net.VpnManager) — protokol platform Android, " +
                "0 dependency pihak ketiga. Belum mendukung split-tunnel per-aplikasi " +
                "(batasan API, bukan belum dikerjakan).",
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
    // Polish pass: this is the app's single most-pressed control — a brief
    // tactile confirmation on tap matches what premium VPN clients (WARP,
    // Mullvad) do for their main toggle, and gives feedback even before the
    // visual ring state finishes updating.
    val haptic = LocalHapticFeedback.current

    // Apple-style pass (batch 2): a brief press-scale-down, the same
    // tactile motion language iOS uses on nearly every tappable control.
    // `interactionSource` only drives this local scale animation — it does
    // NOT replace or wrap the existing onClick/haptic logic below, so the
    // actual toggle behavior is byte-for-byte unchanged from before.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "protectionRingScale"
    )

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
                .scale(scale)
                .clip(CircleShape)
                .background(if (active) ShieldAccentDim else ShieldSurface2)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                ),
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
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface),
        border = BorderStroke(1.dp, if (active) ShieldGreen.copy(alpha = 0.4f) else ShieldOutline)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Accessibility pass (batch 4) — same toggleable-row fix
                // already verified safe on Logs/Whitelist (v3.22.0, CI
                // green): merges icon/title/subtitle/Switch into ONE
                // TalkBack stop and makes the whole row tappable. Haptic +
                // onToggle logic MOVED here unchanged (not duplicated) —
                // the Switch below now has onCheckedChange = null so it
                // can't double-fire when tapped directly.
                modifier = Modifier.toggleable(
                    value = active,
                    enabled = !connecting,
                    role = Role.Switch,
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle(it)
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ShieldAccentDim else ShieldSurface2),
                    contentAlignment = Alignment.Center
                ) {
                    // Polish pass: "Menyambungkan…" used to be static text with
                    // no visual motion at all — indistinguishable at a glance
                    // from any other idle state. A small spinner in the same
                    // icon slot gives the multi-second registration+handshake
                    // window an actual progress cue.
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ShieldTextMuted
                        )
                    } else {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (active) ShieldGreen else ShieldTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                    // Row above owns the toggle via `toggleable` now —
                    // null here prevents a double-fire when the user taps
                    // directly on the Switch's own hit target.
                    onCheckedChange = null,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(
                    value = routeIpv6,
                    role = Role.Switch,
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleRouteIpv6(it)
                    }
                )
            ) {
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
                    // Row above now owns the toggle via `toggleable` — null
                    // here prevents a double-fire when tapping directly on
                    // the Switch's own hit target (same pattern as the two
                    // tunnel cards above, Logs, and Whitelist).
                    onCheckedChange = null,
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

/**
 * v3.15.0 — first card driven by a [com.fdzaki.adshield.protocol.VpnEngine]
 * ([com.fdzaki.adshield.protocol.IkeV2VpnEngine]) instead of a direct
 * Service Intent, mirroring [WarpModeCard]'s layout for visual consistency.
 * No quality/latency row like WARP's — IKEv2's public state API (API 33+
 * only, see IkeV2VpnEngine kdoc) doesn't expose that kind of telemetry.
 */
@Composable
private fun IkeV2ModeCard(
    active: Boolean,
    connecting: Boolean,
    error: String?,
    hasProfile: Boolean,
    onToggle: (Boolean) -> Unit,
    onEditProfile: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ShieldSurface),
        border = BorderStroke(1.dp, if (active) ShieldGreen.copy(alpha = 0.4f) else ShieldOutline)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Same accessibility fix as WarpModeCard above — includes
                // `hasProfile` in `enabled` too, matching the Switch's own
                // existing enabled condition exactly (not a new rule).
                modifier = Modifier.toggleable(
                    value = active,
                    enabled = !connecting && hasProfile,
                    role = Role.Switch,
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle(it)
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ShieldAccentDim else ShieldSurface2),
                    contentAlignment = Alignment.Center
                ) {
                    // Same connecting-spinner treatment as WarpModeCard, for
                    // visual consistency between the app's two tunnel cards.
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ShieldTextMuted
                        )
                    } else {
                        Icon(
                            Icons.Filled.VpnKey,
                            contentDescription = null,
                            tint = if (active) ShieldGreen else ShieldTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("VPN Tunnel (IKEv2)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !hasProfile -> "Belum ada profil tersimpan"
                            connecting -> "Menyambungkan…"
                            active -> "Semua trafik terenkripsi lewat server IKEv2"
                            else -> "Full-tunnel native Android, mode terpisah dari DNS/WARP"
                        },
                        fontSize = 12.sp,
                        color = if (active) ShieldGreen else ShieldTextMuted
                    )
                }
                Switch(
                    checked = active,
                    enabled = !connecting && hasProfile,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = ShieldGreen,
                        checkedThumbColor = ShieldSurface
                    )
                )
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text("Gagal: $error", color = ShieldDanger, fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ShieldOutline, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onEditProfile, enabled = !active && !connecting) {
                Text(
                    if (hasProfile) "Ubah profil server" else "Isi profil server",
                    fontSize = 12.sp,
                    color = ShieldGreen
                )
            }
        }
    }
}

/** Server/identity/username/password form — username+password (EAP-MSCHAPv2)
 *  only for now (matches [VpnProfileRepository.saveIkeV2Profile]); certificate
 *  auth has no UI yet, see that method's kdoc. */
@Composable
private fun IkeV2ProfileDialog(
    initial: VpnProfileRepository.IkeV2StoredProfile?,
    onDismiss: () -> Unit,
    onSave: (server: String, identity: String, username: String, password: String) -> Unit
) {
    var server by remember { mutableStateOf(initial?.serverAddress ?: "") }
    var identity by remember { mutableStateOf(initial?.identity ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    val canSave = server.isNotBlank() && identity.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil server IKEv2") },
        text = {
            Column {
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    label = { Text("Alamat server") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = identity, onValueChange = { identity = it },
                    label = { Text("Identity") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(server.trim(), identity.trim(), username.trim(), password) },
                enabled = canSave
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/** Thousands-separated count for [StatCard] — plain `toString()` reads fine
 *  at small values but turns into an unscannable digit blob once blocked/
 *  allowed counters climb into the thousands over weeks of normal use. */
private fun formatStatCount(count: Long): String =
    NumberFormat.getInstance(Locale("in", "ID")).format(count)

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
