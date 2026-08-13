package com.fdzaki.adshield.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdzaki.adshield.ui.MainViewModel
import com.fdzaki.adshield.ui.SilentLeakUiItem
import com.fdzaki.adshield.ui.components.TactileSurface
import com.fdzaki.adshield.ui.theme.ShieldBgDark
import com.fdzaki.adshield.ui.theme.ShieldDanger
import com.fdzaki.adshield.ui.theme.ShieldTextMuted
import com.fdzaki.adshield.ui.theme.ShieldWhite

/**
 * v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md). Not a generic
 * ad-blocker/VPN feature: shows which installed apps made DNS queries
 * while the SCREEN WAS OFF — i.e. talked to the network while the user
 * wasn't looking at the phone at all. Entirely on-device (ScreenStateMonitor
 * + the existing domain_log Room table), no cloud, no extra permission.
 *
 * v4.7.8 — Tactile wiring (see CHANGELOG.md). This screen never had a
 * Card/Button/Switch to swap 1:1 the way Logs/Rules/Whitelist/Diagnostics/
 * Onboarding did in v4.6.0 — it's a read-only list, nothing interactive.
 * The list is instead wrapped in one [TactileSurface] panel, same pattern
 * as HomeScreen's `NavGroup` ("groups nav rows into a single raised
 * panel") — not per-row cards, which has no precedent anywhere in this
 * app (Logs/Rules/Whitelist all deliberately leave list rows flat).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentLeakScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val leaks by viewModel.silentLeaks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deteksi Aktivitas Diam-diam") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ShieldBgDark,
                    titleContentColor = ShieldWhite,
                    navigationIconContentColor = ShieldWhite
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Aplikasi di bawah ini melakukan query DNS saat layar HP mati — " +
                    "artinya mereka \"menelepon pulang\" tanpa sepengetahuan Anda, " +
                    "bukan cuma saat Anda memakainya.",
                fontSize = 12.sp,
                color = ShieldTextMuted,
                modifier = Modifier.padding(16.dp)
            )

            // weight(1f) is required: a LazyColumn needs a bounded height from
            // its parent or it crashes ("measured with an infinity maximum
            // height constraint"). This outer Column already gets a bounded
            // height from Scaffold's content slot; weight(1f) passes the
            // remaining space down through TactileSurface's inner Column to
            // the LazyColumn below.
            TactileSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (leaks.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Belum ada aktivitas diam-diam terdeteksi.\n" +
                                "Data terkumpul selama proteksi DNS aktif.",
                            color = ShieldTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(leaks, key = { it.packageName }) { item ->
                            SilentLeakRow(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SilentLeakRow(item: SilentLeakUiItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bitmap = item.icon?.toBitmap()?.asImageBitmap()
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ShieldTextMuted.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = ShieldTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(item.label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(item.packageName, fontSize = 11.sp, color = ShieldTextMuted)
        }

        Text(
            "${item.count}x",
            color = ShieldDanger,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
